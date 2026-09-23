import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const loginDuration = new Trend('login_duration');

export const options = {
  stages: [
    { duration: '30s', target: 20 },  // Ramp up to 20 users
    { duration: '1m', target: 20 },   // Stay at 20 users for 1 minute
    { duration: '30s', target: 50 },  // Ramp up to 50 users
    { duration: '1m', target: 50 },   // Stay at 50 users for 1 minute
    { duration: '30s', target: 0 },   // Ramp down to 0 users
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should be below 500ms
    errors: ['rate<0.1'],            // Error rate should be below 10%
  },
};

const BASE_URL = 'http://localhost:8081';

export default function () {
  const username = `loadtest_${__VU}`;
  const email = `${username}@test.com`;
  const password = 'TestPassword123!';

  // Register
  const registerPayload = JSON.stringify({
    username: username,
    email: email,
    password: password,
  });

  const registerParams = {
    headers: { 'Content-Type': 'application/json' },
  };

  const registerRes = http.post(`${BASE_URL}/api/v1/auth/register`, registerPayload, registerParams);

  check(registerRes, {
    'register status is 200': (r) => r.status === 200,
    'register has token': (r) => JSON.parse(r.body).token !== undefined,
  }) || errorRate.add(1);

  loginDuration.add(registerRes.timings.duration);

  // Login
  const loginPayload = JSON.stringify({
    username: username,
    password: password,
  });

  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, loginPayload, registerParams);

  check(loginRes, {
    'login status is 200': (r) => r.status === 200,
    'login has token': (r) => JSON.parse(r.body).token !== undefined,
  }) || errorRate.add(1);

  loginDuration.add(loginRes.timings.duration);

  const token = JSON.parse(loginRes.body).token;

  // Get Profile
  const profileParams = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };

  const profileRes = http.get(`${BASE_URL}/api/v1/auth/me`, profileParams);

  check(profileRes, {
    'profile status is 200': (r) => r.status === 200,
    'profile has username': (r) => JSON.parse(r.body).username !== undefined,
  }) || errorRate.add(1);

  sleep(1);
}
