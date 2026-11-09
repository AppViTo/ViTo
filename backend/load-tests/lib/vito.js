// Utilidades compartidas de los escenarios k6 de la Ola 1 (T061). Todo pasa por el gateway
// (BASE_URL), como en producción: JWT, cabeceras X-User-* y límites de tasa.
import http from 'k6/http';
import { sleep } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const PASSWORD = 'LoadTest-Ola1!';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** Identificador corto y único por corrida, para usernames de hasta 30 caracteres. */
export function runId() {
  return Date.now().toString(36);
}

export function authHeaders(token) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
}

/**
 * POST que respeta el 429 del gateway: espera Retry-After y reintenta. El límite de auth es por
 * IP (10/s, ráfaga de 20), y setup() registra a todos los asistentes desde la misma IP.
 */
export function postWithRetry(url, body, params, tries = 10) {
  let res;
  for (let i = 0; i < tries; i++) {
    res = http.post(url, body, params);
    if (res.status !== 429) {
      return res;
    }
    sleep(Number(res.headers['Retry-After'] || 1));
  }
  return res;
}

/** Registra un usuario y devuelve {id, token}; falla la corrida si no puede. */
export function register(username, displayName) {
  const res = postWithRetry(`${BASE_URL}/api/v1/auth/register`, JSON.stringify({
    username,
    email: `${username}@loadtest.example`,
    password: PASSWORD,
    displayName,
  }), { headers: JSON_HEADERS, tags: { name: 'register' } });
  if (res.status !== 201) {
    throw new Error(`register ${username}: ${res.status} ${res.body}`);
  }
  const body = res.json();
  return { id: body.user.id, token: body.accessToken };
}

/** Crea un evento PUBLISHED que empieza en 7 días; `settings` se mezcla con los valores base. */
export function createEvent(token, title, settings) {
  const start = new Date(Date.now() + 7 * 24 * 3600 * 1000);
  const end = new Date(start.getTime() + 6 * 3600 * 1000);
  const res = postWithRetry(`${BASE_URL}/api/v1/events`, JSON.stringify({
    title,
    description: 'Evento de prueba de carga (T061)',
    details: {
      startDate: start.toISOString(),
      endDate: end.toISOString(),
      location: 'Bogotá',
      address: 'Calle de prueba 1',
      category: 'BIRTHDAY',
    },
    settings: {
      isPublic: true,
      requiresApproval: false,
      allowGuestInvites: false,
      enableLiveStreaming: false,
      enableMosaic: false,
      hideAddressUntilAccepted: true,
      ...settings,
    },
  }), { ...authHeaders(token), tags: { name: 'createEvent' } });
  if (res.status !== 201) {
    throw new Error(`createEvent: ${res.status} ${res.body}`);
  }
  return res.json().id;
}

/** Recorre todas las páginas (size 100, ADR-008) de un listado de array plano. */
export function listAll(url, token, name) {
  const all = [];
  for (let page = 0; page < 50; page++) {
    const sep = url.includes('?') ? '&' : '?';
    const res = http.get(`${url}${sep}page=${page}&size=100`, { ...authHeaders(token), tags: { name } });
    if (res.status !== 200) {
      throw new Error(`${name}: ${res.status} ${res.body}`);
    }
    const items = res.json();
    all.push(...items);
    if (items.length < 100) {
      break;
    }
  }
  return all;
}
