// T061 (Ola 1, SC-004 y FR-009): vista previa pública de un enlace de invitación.
//
// Dos escenarios:
// - latency: p95 < 300 ms de getInviteLinkPreview. Va directo a event-service (PREVIEW_URL),
//   porque el gateway limita la vista previa a 20 peticiones por IP y desde una sola máquina
//   solo se mediría el 429.
// - gateway_limit: por el gateway, una ráfaga de 25 desde la misma IP: las primeras 20 pasan y
//   el resto recibe 429 con Retry-After: 6 (CONVENTIONS §9).
//
// Contra el stack local (docker compose up -d), desde la raíz del repo, dentro de la red de
// Compose para llegar a event-service:
//   docker run --rm -i --network vito_vito-network -e BASE_URL=http://api-gateway:8080 \
//     -e PREVIEW_URL=http://event-service:8083 \
//     -v "$PWD/backend/load-tests:/scripts" grafana/k6:1.8.2 run /scripts/ola-1-preview.js
//
// Variables: BASE_URL (gateway), PREVIEW_URL (event-service), RATE (50 por segundo).
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, authHeaders, createEvent, postWithRetry, register, runId } from './lib/vito.js';

const PREVIEW_URL = __ENV.PREVIEW_URL || 'http://event-service:8083';
const RATE = Number(__ENV.RATE || 50);
const BURST = 20;

const addressLeaked = new Counter('address_leaked');

export const options = {
  setupTimeout: '2m',
  scenarios: {
    latency: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 20,
      maxVUs: 100,
      exec: 'latency',
    },
    gateway_limit: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      startTime: '1m5s',
      exec: 'gatewayLimit',
    },
  },
  thresholds: {
    'http_req_duration{name:getInviteLinkPreview}': ['p(95)<300'],
    'http_req_failed{name:getInviteLinkPreview}': ['rate==0'],
    address_leaked: ['count==0'],
    checks: ['rate==1'],
  },
};

export function setup() {
  const id = runId();
  const organizer = register(`k6p${id}`, 'Organizador k6');
  const eventId = createEvent(organizer.token, `Carga vista previa ${id}`, { isPublic: false, maxAttendees: 100 });
  const res = postWithRetry(`${BASE_URL}/api/v1/events/${eventId}/invite-links`,
    JSON.stringify({ expiresInDays: 7 }), { ...authHeaders(organizer.token), tags: { name: 'createInviteLink' } });
  if (res.status !== 201) {
    throw new Error(`createInviteLink: ${res.status} ${res.body}`);
  }
  return { token: res.json().token };
}

export function latency(data) {
  const res = http.get(`${PREVIEW_URL}/api/v1/invite-links/${data.token}`, { tags: { name: 'getInviteLinkPreview' } });
  check(res, { 'vista previa 200': (r) => r.status === 200 });
  // R3: la vista previa nunca lleva la dirección exacta
  if (res.status === 200 && res.json().address !== undefined) {
    addressLeaked.add(1);
  }
}

export function gatewayLimit(data) {
  const statuses = [];
  let retryAfter = null;
  for (let i = 0; i < BURST + 5; i++) {
    const res = http.get(`${BASE_URL}/api/v1/invite-links/${data.token}`, { tags: { name: 'previewViaGateway' } });
    statuses.push(res.status);
    if (res.status === 429 && retryAfter === null) {
      retryAfter = res.headers['Retry-After'];
    }
  }
  const passed = statuses.filter((s) => s === 200).length;
  const firstLimited = statuses.indexOf(429);
  console.log(`gateway: ${passed} con 200, ${statuses.length - passed} con 429, Retry-After ${retryAfter}`);
  // Pasan como mucho BURST: menos si el bucket de la IP no se rellenó desde la corrida anterior
  // (1 token por segundo y 6 por petición: 2 minutos para llenarse)
  check(null, {
    'gateway deja pasar como mucho la ráfaga': () => passed > 0 && passed <= BURST,
    'gateway corta con 429': () => firstLimited > 0 && statuses.slice(firstLimited).every((s) => s === 429),
    'Retry-After de 6 s': () => retryAfter === '6',
  });
}
