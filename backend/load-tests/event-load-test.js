import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const eventCreationDuration = new Trend('event_creation_duration');

export const options = {
  stages: [
    { duration: '30s', target: 15 },
    { duration: '1m', target: 15 },
    { duration: '30s', target: 30 },
    { duration: '1m', target: 30 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'],
    errors: ['rate<0.1'],
  },
};

const AUTH_URL = 'http://localhost:8081';
const EVENT_URL = 'http://localhost:8083';

function getToken() {
  const username = `loadtest_event_${__VU}_${Date.now()}`;
  const email = `${username}@test.com`;

  const registerRes = http.post(`${AUTH_URL}/api/v1/auth/register`, JSON.stringify({
    username,
    email,
    password: 'TestPassword123!',
  }), { headers: { 'Content-Type': 'application/json' } });

  if (registerRes.status === 200) {
    return JSON.parse(registerRes.body).token;
  }

  const loginRes = http.post(`${AUTH_URL}/api/v1/auth/login`, JSON.stringify({
    username,
    password: 'TestPassword123!',
  }), { headers: { 'Content-Type': 'application/json' } });

  if (loginRes.status === 200) {
    return JSON.parse(loginRes.body).token;
  }

  return null;
}

export default function () {
  const token = getToken();
  if (!token) {
    errorRate.add(1);
    return;
  }

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };

  // Create Event
  const createEventRes = http.post(`${EVENT_URL}/api/v1/events`, JSON.stringify({
    title: `Load Test Event ${__VU}`,
    description: 'Event created during load testing',
    date: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    location: 'Load Test Location',
    maxAttendees: 100,
  }), params);

  check(createEventRes, {
    'create event status is 201': (r) => r.status === 201,
    'create event has id': (r) => JSON.parse(r.body).id !== undefined,
  }) || errorRate.add(1);

  eventCreationDuration.add(createEventRes.timings.duration);

  const eventId = JSON.parse(createEventRes.body).id;

  // Get Event
  const getEventRes = http.get(`${EVENT_URL}/api/v1/events/${eventId}`, params);

  check(getEventRes, {
    'get event status is 200': (r) => r.status === 200,
    'get event has title': (r) => JSON.parse(r.body).title !== undefined,
  }) || errorRate.add(1);

  // List Events
  const listEventsRes = http.get(`${EVENT_URL}/api/v1/events?page=0&size=20`, params);

  check(listEventsRes, {
    'list events status is 200': (r) => r.status === 200,
    'list events has content': (r) => JSON.parse(r.body).content !== undefined,
  }) || errorRate.add(1);

  sleep(1);
}
