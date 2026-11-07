// T061 (Ola 1, SC-002 y FR-015): 150 asistentes responden "voy" a la vez a un evento con cupo
// CAPACITY. El cupo nunca se supera: exactamente CAPACITY confirmados y el resto en lista de
// espera, con p95 < 1 s en el RSVP.
//
// Contra el stack local (docker compose up -d), desde la raíz del repo:
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 \
//     -v "$PWD/backend/load-tests:/scripts" grafana/k6:1.8.2 run /scripts/ola-1-rsvp.js
//
// Variables: BASE_URL (gateway), ATTENDEES (150), CAPACITY (100).
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, authHeaders, createEvent, listAll, register, runId } from './lib/vito.js';

const ATTENDEES = Number(__ENV.ATTENDEES || 150);
const CAPACITY = Number(__ENV.CAPACITY || 100);

const capacityExceeded = new Counter('capacity_exceeded');
const unexpectedAnswer = new Counter('unexpected_answer');

export const options = {
  setupTimeout: '5m',
  scenarios: {
    // Una respuesta por asistente, todas a la vez (cada VU es un asistente)
    rsvp: {
      executor: 'per-vu-iterations',
      vus: ATTENDEES,
      iterations: 1,
      maxDuration: '2m',
    },
  },
  thresholds: {
    'http_req_duration{name:respondRsvp}': ['p(95)<1000'],
    'http_req_failed{name:respondRsvp}': ['rate==0'],
    capacity_exceeded: ['count==0'],
    unexpected_answer: ['count==0'],
    checks: ['rate==1'],
  },
};

export function setup() {
  const id = runId();
  const organizer = register(`k6o${id}`, 'Organizador k6');
  const eventId = createEvent(organizer.token, `Carga RSVP ${id}`, { maxAttendees: CAPACITY });
  const guests = [];
  for (let i = 0; i < ATTENDEES; i++) {
    guests.push(register(`k6g${id}_${i}`, `Invitado ${i}`).token);
  }
  return { eventId, organizerToken: organizer.token, guests };
}

export default function (data) {
  const token = data.guests[__VU - 1];
  const res = http.post(`${BASE_URL}/api/v1/events/${data.eventId}/rsvp`,
    JSON.stringify({ status: 'ACCEPTED' }), { ...authHeaders(token), tags: { name: 'respondRsvp' } });
  const ok = check(res, {
    'RSVP 200': (r) => r.status === 200,
    'confirmado o en espera': (r) => r.status === 200 && ['ACCEPTED', 'WAITLISTED'].includes(r.json().status),
  });
  if (!ok) {
    unexpectedAnswer.add(1);
    // El código y el error del envelope, sin el token: para saber qué falló
    let error = '';
    try { error = res.json().error || ''; } catch (e) { error = 'sin JSON'; }
    console.warn(`RSVP inesperado: ${res.status} ${error}`);
  }
}

export function teardown(data) {
  const base = `${BASE_URL}/api/v1/events/${data.eventId}`;
  const event = http.get(base, { ...authHeaders(data.organizerToken), tags: { name: 'getEvent' } }).json();
  const accepted = listAll(`${base}/attendees?status=ACCEPTED`, data.organizerToken, 'listEventAttendees');
  const waitlisted = listAll(`${base}/attendees?status=WAITLISTED`, data.organizerToken, 'listEventAttendees');
  const expectedAccepted = Math.min(ATTENDEES, CAPACITY);

  console.log(`cupo ${CAPACITY}: confirmados ${accepted.length} (stats ${event.stats.totalAccepted}), `
    + `en espera ${waitlisted.length}`);
  if (accepted.length > CAPACITY || event.stats.totalAccepted > CAPACITY) {
    capacityExceeded.add(1);
  }
  check(null, {
    'confirmados = cupo': () => accepted.length === expectedAccepted,
    'stats.totalAccepted = confirmados': () => event.stats.totalAccepted === accepted.length,
    'el resto en lista de espera': () => waitlisted.length === ATTENDEES - expectedAccepted,
  });
}
