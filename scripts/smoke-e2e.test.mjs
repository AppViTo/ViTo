import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { runSmoke, redact, SmokeError } from './smoke-e2e.mjs';

const EVENT = {
  id: 'evt_1',
  organizerId: 'usr_1',
  title: 'Evento E2E',
  details: { startDate: '2027-01-01T00:00:00Z', category: 'PARTY' },
  settings: { isPublic: true },
  status: 'PUBLISHED',
};

function authResponse(accessToken, refreshToken = 'refresh-1') {
  return {
    accessToken,
    refreshToken,
    expiresIn: 900,
    user: { id: 'usr_1', username: 'e2e', email: 'e2e@example.com', displayName: 'E2E', avatar: null },
  };
}

function errorBody(status, error) {
  return { error, message: 'mensaje', status, timestamp: '2026-10-28T00:00:00Z' };
}

function send(res, status, body) {
  const payload = body === undefined ? '' : JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const text = Buffer.concat(chunks).toString('utf8');
  return text.length > 0 ? JSON.parse(text) : undefined;
}

function handlerWith({ listStatus = 200, loginStatus = 200, loginShape = 'ok' } = {}) {
  const revoked = new Set();
  let loginAttempts = 0;

  return async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const auth = req.headers.authorization?.replace(/^Bearer /, '');
    const authorized = auth && !revoked.has(auth);

    if (req.method === 'POST' && url.pathname === '/api/v1/auth/login') {
      loginAttempts += 1;
      if (loginStatus === 429) {
        if (loginAttempts === 1) {
          res.writeHead(429, { 'Retry-After': '0', 'Content-Type': 'application/json' });
          res.end(JSON.stringify(errorBody(429, 'RATE_LIMITED')));
          return;
        }
        send(res, 200, authResponse('access-1'));
        return;
      }
      if (loginStatus !== 200) {
        send(res, loginStatus, errorBody(loginStatus, 'UNAUTHORIZED'));
        return;
      }
      send(res, 200, loginShape === 'ok' ? authResponse('access-1') : { accessToken: 'solo-access' });
      return;
    }

    if (req.method === 'POST' && url.pathname === '/api/v1/auth/refresh') {
      await readBody(req);
      send(res, 200, authResponse('access-2', 'refresh-2'));
      return;
    }

    if (req.method === 'POST' && url.pathname === '/api/v1/auth/logout') {
      if (!authorized) {
        send(res, 401, errorBody(401, 'UNAUTHORIZED'));
        return;
      }
      revoked.add(auth);
      res.writeHead(204);
      res.end();
      return;
    }

    if (req.method === 'GET' && url.pathname === '/api/v1/events') {
      if (!authorized) {
        send(res, 401, errorBody(401, 'UNAUTHORIZED'));
        return;
      }
      if (listStatus !== 200) {
        send(res, listStatus, errorBody(listStatus, 'NOT_FOUND'));
        return;
      }
      send(res, 200, [EVENT]);
      return;
    }

    if (req.method === 'GET' && url.pathname === '/api/v1/events/evt_1') {
      if (!authorized) {
        send(res, 401, errorBody(401, 'UNAUTHORIZED'));
        return;
      }
      send(res, 200, EVENT);
      return;
    }

    send(res, 404, errorBody(404, 'NOT_FOUND'));
  };
}

async function withServer(options, fn) {
  const server = http.createServer(handlerWith(options));
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  try {
    return await fn(`http://127.0.0.1:${port}`);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

function config(baseUrl, extra = {}) {
  return {
    baseUrl,
    email: 'e2e@example.com',
    password: 'secreto',
    eventId: 'evt_1',
    log: () => {},
    ...extra,
  };
}

test('recorre el camino crítico del contrato', async () => {
  await withServer({}, async (baseUrl) => {
    const result = await runSmoke(config(baseUrl));
    assert.equal(result.ok, true);
    assert.equal(result.results.filter((item) => item.warn).length, 0);
    assert.ok(result.results.some((item) => item.name === 'POST /auth/logout'));
    assert.ok(result.results.some((item) => item.name === 'GET /events con token revocado'));
  });
});

test('avisa (sin fallar) cuando listEvents no está implementado', async () => {
  await withServer({ listStatus: 405 }, async (baseUrl) => {
    const result = await runSmoke(config(baseUrl));
    assert.equal(result.ok, true);
    assert.ok(result.results.some((item) => item.warn && item.note.includes('405')));
  });
});

test('en modo estricto, listEvents sin implementar falla', async () => {
  await withServer({ listStatus: 405 }, async (baseUrl) => {
    await assert.rejects(() => runSmoke(config(baseUrl, { strict: true })), SmokeError);
  });
});

test('falla si la forma de AuthResponse no cumple ADR-004', async () => {
  await withServer({ loginShape: 'bad' }, async (baseUrl) => {
    await assert.rejects(
      () => runSmoke(config(baseUrl)),
      /falta refreshToken/,
    );
  });
});

test('reintenta una vez si el gateway responde 429', async () => {
  await withServer({ loginStatus: 429 }, async (baseUrl) => {
    const result = await runSmoke(config(baseUrl));
    assert.equal(result.ok, true);
  });
});

test('redacta tokens y contraseñas en los mensajes', () => {
  const safe = redact({
    accessToken: 'a',
    refreshToken: 'b',
    password: 'c',
    nested: { authorization: 'd', email: 'e2e@example.com' },
  });
  assert.deepEqual(safe, {
    accessToken: '[redacted]',
    refreshToken: '[redacted]',
    password: '[redacted]',
    nested: { authorization: '[redacted]', email: 'e2e@example.com' },
  });
});
