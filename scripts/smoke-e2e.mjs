#!/usr/bin/env node
import { pathToFileURL } from 'node:url';

const DEFAULT_TIMEOUT_MS = 10000;
const REDACTED = '[redacted]';
const SECRET_KEY = /token|password|secret|authorization/i;

export class SmokeError extends Error {
  constructor(message) {
    super(message);
    this.name = 'SmokeError';
  }
}

export function redact(value) {
  if (Array.isArray(value)) return value.map(redact);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [
        key,
        SECRET_KEY.test(key) ? REDACTED : redact(item),
      ]),
    );
  }
  return value;
}

function apiBase(raw) {
  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new SmokeError(`E2E_BASE_URL no es una URL válida: ${raw}`);
  }
  if (!/^https?:$/.test(url.protocol)) {
    throw new SmokeError(`E2E_BASE_URL debe ser http(s): ${raw}`);
  }
  const path = url.pathname.replace(/\/+$/, '');
  url.pathname = path.endsWith('/api/v1') ? path : `${path}/api/v1`.replace(/\/{2,}/g, '/');
  url.search = '';
  url.hash = '';
  return url.toString().replace(/\/$/, '');
}

async function request(base, method, path, { token, body, timeoutMs } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  const started = performance.now();
  let response;
  try {
    response = await fetch(`${base}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'manual',
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch (error) {
    const elapsed = Math.round(performance.now() - started);
    const reason = error?.name === 'TimeoutError' ? `timeout de ${timeoutMs} ms` : error?.message;
    throw new SmokeError(`${method} ${path}: sin respuesta (${elapsed} ms): ${reason}`);
  }

  const elapsed = Math.round(performance.now() - started);
  const text = await response.text();
  let payload;
  if (text.length > 0) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = text;
    }
  }
  return { status: response.status, body: payload, headers: response.headers, ms: elapsed };
}

async function call(base, method, path, options, retries) {
  let response = await request(base, method, path, options);
  if (response.status === 429 && retries > 0) {
    const retryAfter = Number(response.headers.get('retry-after'));
    const waitMs = Number.isFinite(retryAfter) && retryAfter >= 0
      ? Math.min(retryAfter * 1000, 5000)
      : 1000;
    await new Promise((resolve) => setTimeout(resolve, waitMs));
    response = await request(base, method, path, options);
  }
  return response;
}

function assert(condition, message) {
  if (!condition) throw new SmokeError(message);
}

function assertStatus(response, expected, label) {
  if (response.status !== expected) {
    const body = response.body === undefined ? '' : ` · ${JSON.stringify(redact(response.body))}`;
    throw new SmokeError(`${label}: esperaba ${expected}, recibió ${response.status}${body}`);
  }
}

function assertErrorEnvelope(response, expectedStatus, expectedError, label) {
  assertStatus(response, expectedStatus, label);
  const body = response.body;
  assert(body && typeof body === 'object' && !Array.isArray(body), `${label}: el error no es un objeto JSON`);
  assert(body.error === expectedError, `${label}: error "${body.error}", esperaba "${expectedError}"`);
  assert(body.status === expectedStatus, `${label}: status del cuerpo ${body.status}, esperaba ${expectedStatus}`);
  assert(typeof body.message === 'string' && body.message.length > 0, `${label}: falta message`);
  assert(!Number.isNaN(Date.parse(body.timestamp)), `${label}: timestamp inválido`);
}

function assertAuthResponse(body, label) {
  assert(body && typeof body === 'object', `${label}: la respuesta no es un objeto JSON`);
  assert(typeof body.accessToken === 'string' && body.accessToken.length > 0, `${label}: falta accessToken`);
  assert(typeof body.refreshToken === 'string' && body.refreshToken.length > 0, `${label}: falta refreshToken`);
  assert(typeof body.expiresIn === 'number' && body.expiresIn > 0, `${label}: expiresIn debe ser un número positivo`);
  assert(body.user && typeof body.user.id === 'string' && body.user.id.length > 0, `${label}: falta user.id`);
}

export async function runSmoke({
  baseUrl,
  email,
  password,
  eventId,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  strict = false,
  retries = 1,
  log = console.log,
} = {}) {
  assert(typeof baseUrl === 'string' && baseUrl.length > 0, 'falta E2E_BASE_URL');
  assert(typeof email === 'string' && email.length > 0, 'falta E2E_EMAIL');
  assert(typeof password === 'string' && password.length > 0, 'falta E2E_PASSWORD');

  const base = apiBase(baseUrl);
  const results = [];

  const record = (name, response, note, warn = false) => {
    results.push({ name, ms: response?.ms ?? 0, note, warn });
    log(`[${warn ? 'WARN' : 'OK'}] ${name} (${response?.ms ?? 0} ms)${note ? ` · ${note}` : ''}`);
  };

  const step = async (name, fn) => {
    try {
      await fn();
    } catch (error) {
      if (error instanceof SmokeError) throw error;
      throw new SmokeError(`${name}: ${error?.message ?? error}`);
    }
  };

  await step('GET /api/v1/events sin token', async () => {
    const response = await call(base, 'GET', '/events', { timeoutMs }, retries);
    assertErrorEnvelope(response, 401, 'UNAUTHORIZED', 'GET /events sin token');
    record('GET /events sin token', response, '401 UNAUTHORIZED');
  });

  let accessToken;
  let refreshToken;

  await step('POST /api/v1/auth/login', async () => {
    const response = await call(base, 'POST', '/auth/login', {
      body: { email, password },
      timeoutMs,
    }, retries);
    assertStatus(response, 200, 'POST /auth/login');
    assertAuthResponse(response.body, 'POST /auth/login');
    accessToken = response.body.accessToken;
    refreshToken = response.body.refreshToken;
    record('POST /auth/login', response, `200 · ${response.body.user.id}`);
  });

  const listEvents = async (scope, response) => {
    if (response.status === 200) {
      assert(Array.isArray(response.body), `GET /events?scope=${scope}: se esperaba un array plano (ADR-002)`);
      record(`GET /events?scope=${scope}`, response, `200 · ${response.body.length} evento(s)`);
      return response.body;
    }
    if ([404, 405].includes(response.status) && !strict) {
      record(
        `GET /events?scope=${scope}`,
        response,
        `${response.status} · contrato pendiente en el backend (AGENT-BRIEF §6)`,
        true,
      );
      return undefined;
    }
    assertStatus(response, 200, `GET /events?scope=${scope}`);
  };

  let firstEventId = typeof eventId === 'string' && eventId.length > 0 ? eventId : undefined;

  await step('GET /api/v1/events?scope=mine', async () => {
    const response = await call(base, 'GET', '/events?scope=mine&size=5', { token: accessToken, timeoutMs }, retries);
    const events = await listEvents('mine', response);
    if (!firstEventId && Array.isArray(events) && events.length > 0) {
      firstEventId = events[0]?.id;
    }
  });

  await step('GET /api/v1/events?scope=upcoming', async () => {
    const response = await call(base, 'GET', '/events?scope=upcoming&size=5', { token: accessToken, timeoutMs }, retries);
    await listEvents('upcoming', response);
  });

  await step('GET /api/v1/events/{eventId}', async () => {
    if (!firstEventId) {
      results.push({ name: 'GET /events/{eventId}', ms: 0, note: 'omitido: sin eventId', warn: true });
      log('[WARN] GET /events/{eventId} · omitido: sin eventId (define E2E_EVENT_ID o ten un evento propio)');
      return;
    }
    const response = await call(base, 'GET', `/events/${encodeURIComponent(firstEventId)}`, {
      token: accessToken,
      timeoutMs,
    }, retries);
    assertStatus(response, 200, 'GET /events/{eventId}');
    assert(response.body?.id === firstEventId, `GET /events/{eventId}: id ${response.body?.id}, esperaba ${firstEventId}`);
    assert(response.body?.details && typeof response.body.details === 'object', 'GET /events/{eventId}: falta details (ADR-001)');
    record('GET /events/{eventId}', response, `200 · ${firstEventId}`);
  });

  await step('POST /api/v1/auth/refresh', async () => {
    const response = await call(base, 'POST', '/auth/refresh', {
      body: { refreshToken },
      timeoutMs,
    }, retries);
    assertStatus(response, 200, 'POST /auth/refresh');
    assertAuthResponse(response.body, 'POST /auth/refresh');
    accessToken = response.body.accessToken;
    refreshToken = response.body.refreshToken;
    record('POST /auth/refresh', response, '200');
  });

  await step('POST /api/v1/auth/logout', async () => {
    const response = await call(base, 'POST', '/auth/logout', { token: accessToken, timeoutMs }, retries);
    assertStatus(response, 204, 'POST /auth/logout');
    assert(response.body === undefined || response.body === '', 'POST /auth/logout: 204 no debe llevar cuerpo (ADR-004)');
    record('POST /auth/logout', response, '204');
  });

  await step('GET /api/v1/events con token revocado', async () => {
    const response = await call(base, 'GET', '/events', { token: accessToken, timeoutMs }, retries);
    assertErrorEnvelope(response, 401, 'UNAUTHORIZED', 'GET /events con token revocado');
    record('GET /events con token revocado', response, '401 UNAUTHORIZED');
  });

  const warnings = results.filter((result) => result.warn);
  log(`SMOKE OK: ${results.length} comprobaciones, ${warnings.length} aviso(s)`);
  return { ok: true, results };
}

export async function main(env = process.env) {
  const timeoutMs = Number(env.E2E_TIMEOUT_MS) > 0 ? Number(env.E2E_TIMEOUT_MS) : DEFAULT_TIMEOUT_MS;
  const strict = env.E2E_STRICT === '1' || env.E2E_STRICT === 'true';
  try {
    await runSmoke({
      baseUrl: env.E2E_BASE_URL,
      email: env.E2E_EMAIL,
      password: env.E2E_PASSWORD,
      eventId: env.E2E_EVENT_ID,
      timeoutMs,
      strict,
    });
    return 0;
  } catch (error) {
    const message = error instanceof SmokeError ? error.message : error?.stack ?? String(error);
    console.error(`[FAIL] ${message}`);
    console.error('SMOKE FAIL');
    return 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = await main();
}
