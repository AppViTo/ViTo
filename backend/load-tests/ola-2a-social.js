// T056 (Ola 2a, T-I30): búsqueda de usuarios (SC-003, p95 < 1 s) y perfil y listas del grafo
// social (p95 < 300 ms), por el gateway. Además comprueba el límite de búsqueda (1/s por
// usuario con ráfaga de 10, research R9): una ráfaga de 15 de un mismo usuario recibe 429 con
// Retry-After: 1 y el error RATE_LIMITED con el mensaje de searchUsers.429.json.
//
// En local vale para corrección (respuestas, contadores, 429); el p95 se mide en un entorno
// representativo (los umbrales de latencia fallan en local sin que eso diga nada de prod).
//
// Contra el stack local (docker compose up -d), desde la raíz del repo:
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 \
//     -v "$PWD/backend/load-tests:/scripts" grafana/k6:1.8.2 run /scripts/ola-2a-social.js
//
// Variables: BASE_URL (gateway), FANS (60, usuarios que siguen a la estrella), VUS (10 por
// escenario; en local, con más, los JVM de 512 MB se saturan y social responde 503 por timeout
// hacia auth, FR-012a), DURATION (30s).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, authHeaders, postWithRetry, register, runId } from './lib/vito.js';

const FANS = Number(__ENV.FANS || 60);
const VUS = Math.min(Number(__ENV.VUS || 10), FANS);
const DURATION = __ENV.DURATION || '30s';
// La estrella sigue a los primeros FOLLOWED fans, para que "seguidos" no esté vacía.
const FOLLOWED = Math.min(10, FANS);

const unexpectedAnswer = new Counter('unexpected_answer');
const countMismatch = new Counter('count_mismatch');

export const options = {
  setupTimeout: '5m',
  scenarios: {
    // Cada VU es un fan distinto (el límite es por usuario) y busca a ritmo humano, por debajo
    // de 1/s: aquí no debe haber ningún 429.
    search: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: 'search',
    },
    // Perfil, seguidores y seguidos de la estrella: 3 peticiones cada ~0,6 s por usuario, por
    // debajo de los 10/s de la ruta social.
    social: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: 'social',
    },
    // Al final, un solo usuario busca 15 veces seguidas: la ráfaga de 10 pasa y el resto, 429.
    rateLimit: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      startTime: DURATION,
      exec: 'rateLimit',
    },
  },
  thresholds: {
    'http_req_duration{name:searchUsers}': ['p(95)<1000'],
    'http_req_duration{name:getSocialProfile}': ['p(95)<300'],
    'http_req_duration{name:listFollowers}': ['p(95)<300'],
    'http_req_duration{name:listFollowing}': ['p(95)<300'],
    'http_req_failed{scenario:search}': ['rate==0'],
    'http_req_failed{scenario:social}': ['rate==0'],
    unexpected_answer: ['count==0'],
    count_mismatch: ['count==0'],
    checks: ['rate==1'],
  },
};

export function setup() {
  const id = runId();
  // Prefijo común de los fans: la búsqueda es por prefijo de username.
  const prefix = `k6f${id}`;
  const star = register(`k6s${id}`, 'Estrella k6');
  const fans = [];
  for (let i = 0; i < FANS; i++) {
    fans.push(register(`${prefix}_${i}`, `Fan ${i}`));
  }
  fans.forEach((fan) => follow(fan.token, star.id));
  fans.slice(0, FOLLOWED).forEach((fan) => follow(star.token, fan.id));
  // Un usuario aparte para la ráfaga: su bucket de búsqueda está lleno.
  const burster = register(`k6b${id}`, 'Ráfaga k6');
  return { prefix, star, fans, burster };
}

function follow(token, userId) {
  const res = postWithRetry(`${BASE_URL}/api/v1/social/follow/${userId}`, null,
    { ...authHeaders(token), tags: { name: 'follow' } });
  if (res.status !== 204) {
    throw new Error(`follow ${userId}: ${res.status} ${res.body}`);
  }
}

function report(name, res) {
  unexpectedAnswer.add(1);
  // El código y el error del envelope, sin el token: para saber qué falló
  let error = '';
  try { error = res.json().error || ''; } catch (e) { error = 'sin JSON'; }
  console.warn(`${name} inesperado: ${res.status} ${error}`);
}

export function search(data) {
  // __VU es único en toda la prueba, no por escenario: cada VU queda con un fan distinto.
  const fan = data.fans[(__VU - 1) % data.fans.length];
  const res = http.get(`${BASE_URL}/api/v1/users/search?query=${data.prefix}&size=20`,
    { ...authHeaders(fan.token), tags: { name: 'searchUsers' } });
  const ok = check(res, {
    'búsqueda 200': (r) => r.status === 200,
    'búsqueda con resultados del prefijo': (r) => r.status === 200 && r.json().length > 0
      && r.json().every((u) => u.username.startsWith(data.prefix)),
    'búsqueda sin email': (r) => r.status === 200 && r.json().every((u) => !('email' in u)),
  });
  if (!ok) {
    report('searchUsers', res);
  }
  sleep(1.2);
}

export function social(data) {
  const fan = data.fans[(__VU - 1) % data.fans.length];
  const params = (name) => ({ ...authHeaders(fan.token), tags: { name } });
  const base = `${BASE_URL}/api/v1/social`;

  const profile = http.get(`${base}/profile/${data.star.id}`, params('getSocialProfile'));
  const isFollowed = (__VU - 1) % data.fans.length < FOLLOWED;
  const profileOk = check(profile, {
    'perfil 200': (r) => r.status === 200,
    'perfil: seguidores = fans': (r) => r.status === 200 && r.json().followersCount === FANS,
    'perfil: seguidos = FOLLOWED': (r) => r.status === 200 && r.json().followingCount === FOLLOWED,
    'perfil: isFollowing': (r) => r.status === 200 && r.json().isFollowing === true,
    'perfil: isFollowedBy': (r) => r.status === 200 && r.json().isFollowedBy === isFollowed,
  });
  if (!profileOk) {
    report('getSocialProfile', profile);
  }

  const followers = http.get(`${base}/followers/${data.star.id}?page=0&size=50`, params('listFollowers'));
  const followersOk = check(followers, {
    'seguidores 200': (r) => r.status === 200,
    'seguidores: primera página llena': (r) => r.status === 200 && r.json().length === Math.min(50, FANS),
  });
  if (!followersOk) {
    report('listFollowers', followers);
  }

  const following = http.get(`${base}/following/${data.star.id}?page=0&size=50`, params('listFollowing'));
  const followingOk = check(following, {
    'seguidos 200': (r) => r.status === 200,
    'seguidos: FOLLOWED': (r) => r.status === 200 && r.json().length === FOLLOWED,
  });
  if (!followingOk) {
    report('listFollowing', following);
  }
  sleep(0.6);
}

export function rateLimit(data) {
  // En paralelo: en serie, con latencias de segundos, el bucket se repone (1/s) entre petición
  // y petición y la ráfaga nunca se agota.
  const params = { ...authHeaders(data.burster.token), tags: { name: 'searchUsersBurst' },
    responseCallback: http.expectedStatuses(200, 429) };
  const url = `${BASE_URL}/api/v1/users/search?query=${data.prefix}`;
  const responses = http.batch(Array.from({ length: 15 }, () => ['GET', url, null, params]));
  const statuses = responses.map((r) => r.status);
  const limited = responses.find((r) => r.status === 429) || null;
  const ok = statuses.filter((s) => s === 200).length;
  console.log(`ráfaga de 15 búsquedas: ${ok} con 200 y ${statuses.length - ok} con 429`);
  check(limited, {
    'ráfaga: hay 429': (r) => r !== null,
    'ráfaga: solo 200 o 429': () => statuses.every((s) => s === 200 || s === 429),
    'ráfaga: Retry-After 1': (r) => r !== null && r.headers['Retry-After'] === '1',
    'ráfaga: RATE_LIMITED': (r) => r !== null && r.json().error === 'RATE_LIMITED',
    // El texto único del gateway para todo 429 (CONVENTIONS §9, searchUsers.429.json tras #341)
    'ráfaga: mensaje del 429': (r) => r !== null
      && r.json().message === 'Has superado el límite de peticiones. Reintenta en 1 segundo',
  });
}

/** Recorre todas las páginas de seguidores de la estrella (size 50, research R7). */
export function teardown(data) {
  const seen = new Set();
  let pages = 0;
  for (let page = 0; page < 100; page++) {
    const res = http.get(`${BASE_URL}/api/v1/social/followers/${data.star.id}?page=${page}&size=50`,
      { ...authHeaders(data.star.token), tags: { name: 'listFollowers' } });
    if (res.status !== 200) {
      report('listFollowers', res);
      return;
    }
    const items = res.json();
    pages++;
    items.forEach((u) => seen.add(u.userId));
    if (items.length < 50) {
      break;
    }
  }
  console.log(`seguidores de la estrella: ${seen.size} distintos en ${pages} páginas (esperados ${FANS})`);
  const allFans = data.fans.every((fan) => seen.has(fan.id));
  if (seen.size !== FANS || !allFans) {
    countMismatch.add(1);
  }
  check(null, {
    'todos los fans una sola vez en las listas': () => seen.size === FANS && allFans,
  });
}
