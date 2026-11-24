import test from 'node:test';
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';
import { startMockWs, matchChannel, MOCK_USER } from './mock-ws.mjs';

/** Cliente que guarda los frames recibidos y permite esperar el siguiente. */
function connect(port, path) {
  const socket = new WebSocket(`ws://127.0.0.1:${port}${path}`);
  const received = [];
  const waiters = [];
  socket.on('message', (raw) => {
    const frame = JSON.parse(raw.toString());
    const waiter = waiters.shift();
    if (waiter) waiter(frame);
    else received.push(frame);
  });
  const closed = new Promise((resolve) => socket.on('close', (code) => resolve(code)));
  const open = new Promise((resolve, reject) => {
    socket.once('open', resolve);
    socket.once('error', reject);
  });
  return {
    socket,
    open,
    closed,
    next: () =>
      received.length
        ? Promise.resolve(received.shift())
        : new Promise((resolve) => waiters.push(resolve)),
    send: (frame) => socket.send(JSON.stringify(frame)),
    /** No llega ningún frame en [ms] milisegundos. */
    nothingWithin: async (ms = 150) => {
      await new Promise((resolve) => setTimeout(resolve, ms));
      return received.length === 0;
    },
  };
}

/** Espera hasta que [condition] se cumpla (el cierre del socket es asíncrono). */
async function eventually(condition, timeoutMs = 2000) {
  const start = Date.now();
  while (!condition()) {
    if (Date.now() - start > timeoutMs) throw new Error('timeout esperando la condición');
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
}

test('matchChannel reconoce las 4 rutas de asyncapi.yaml', () => {
  assert.deepEqual(matchChannel('/ws/chat'), { channel: '/ws/chat' });
  assert.deepEqual(matchChannel('/ws/checkin/evt_1'), { channel: '/ws/checkin/{eventId}', id: 'evt_1' });
  assert.deepEqual(matchChannel('/ws/streaming/str_1'), { channel: '/ws/streaming/{streamId}', id: 'str_1' });
  assert.equal(matchChannel('/ws/otra'), null);
});

test('mock:ws', async (t) => {
  const mock = await startMockWs({ port: 0, host: '127.0.0.1' });
  t.after(() => mock.close());

  await t.test('sin token cierra con 4401', async () => {
    const client = connect(mock.port, '/ws/chat');
    assert.equal(await client.closed, 4401);
  });

  await t.test('PING responde PONG en streaming, el único canal con latido', async () => {
    const client = connect(mock.port, '/ws/streaming/str_ping?token=t');
    assert.equal((await client.next()).type, 'VIEWER_COUNT');
    client.send({ type: 'PING' });
    assert.deepEqual(await client.next(), { type: 'PONG' });
    client.socket.close();
  });

  await t.test('PING en el chat no es un frame del contrato: ERROR', async () => {
    const client = connect(mock.port, '/ws/chat?token=t');
    await client.open;
    client.send({ type: 'PING' });
    const frame = await client.next();
    assert.equal(frame.type, 'ERROR');
    assert.equal(frame.data.error, 'VALIDATION_ERROR');
    client.socket.close();
  });

  await t.test('chat: JOIN_ROOM y SEND_MESSAGE llegan a toda la sala', async () => {
    const ana = connect(mock.port, '/ws/chat?token=a');
    const beto = connect(mock.port, '/ws/chat?token=b');
    await Promise.all([ana.open, beto.open]);

    ana.send({ type: 'JOIN_ROOM', roomId: 'room_1' });
    beto.send({ type: 'JOIN_ROOM', roomId: 'room_1' });
    assert.deepEqual(await ana.next(), { type: 'ROOM_JOINED', data: { roomId: 'room_1' } });
    assert.deepEqual(await beto.next(), { type: 'ROOM_JOINED', data: { roomId: 'room_1' } });

    ana.send({ type: 'SEND_MESSAGE', roomId: 'room_1', content: 'hola' });
    for (const client of [ana, beto]) {
      const frame = await client.next();
      assert.equal(frame.type, 'MESSAGE');
      assert.equal(frame.data.content, 'hola');
      assert.equal(frame.data.roomId, 'room_1');
      assert.equal(frame.data.senderId, MOCK_USER.userId);
    }
    ana.socket.close();
    beto.socket.close();
  });

  await t.test('chat: un frame con identidad del cliente responde ERROR', async () => {
    const client = connect(mock.port, '/ws/chat?token=a');
    await client.open;
    // Dentro de la sala: el ERROR tiene que venir de la identidad, no de la sala.
    client.send({ type: 'JOIN_ROOM', roomId: 'room_id' });
    assert.equal((await client.next()).type, 'ROOM_JOINED');

    client.send({ type: 'SEND_MESSAGE', roomId: 'room_id', content: 'hola', senderId: 'usr_falso' });
    const frame = await client.next();
    assert.equal(frame.type, 'ERROR');
    assert.equal(frame.data.error, 'VALIDATION_ERROR');
    // No se difundió nada.
    assert.ok(await client.nothingWithin());
    client.socket.close();
  });

  await t.test('chat: sin JOIN_ROOM, SEND_MESSAGE, TYPING y MARK_READ responden FORBIDDEN', async () => {
    const member = connect(mock.port, '/ws/chat?token=m');
    const outsider = connect(mock.port, '/ws/chat?token=o');
    await Promise.all([member.open, outsider.open]);
    member.send({ type: 'JOIN_ROOM', roomId: 'room_priv' });
    assert.equal((await member.next()).type, 'ROOM_JOINED');

    for (const frame of [
      { type: 'SEND_MESSAGE', roomId: 'room_priv', content: 'hola' },
      { type: 'TYPING', roomId: 'room_priv', isTyping: true },
      { type: 'MARK_READ', roomId: 'room_priv' },
    ]) {
      outsider.send(frame);
      const reply = await outsider.next();
      assert.equal(reply.type, 'ERROR', frame.type);
      assert.equal(reply.data.error, 'FORBIDDEN', frame.type);
    }
    // A quien sí está en la sala no le llegó nada.
    assert.ok(await member.nothingWithin());
    member.socket.close();
    outsider.socket.close();
  });

  await t.test('chat y streaming: las salas y streams vacíos se borran', async () => {
    await eventually(() => mock.stats().rooms === 0 && mock.stats().streams === 0);
    const client = connect(mock.port, '/ws/chat?token=a');
    await client.open;
    client.send({ type: 'JOIN_ROOM', roomId: 'room_tmp' });
    await client.next();
    assert.equal(mock.stats().rooms, 1);
    client.send({ type: 'LEAVE_ROOM', roomId: 'room_tmp' });
    assert.equal((await client.next()).type, 'ROOM_LEFT');
    assert.equal(mock.stats().rooms, 0);

    client.send({ type: 'JOIN_ROOM', roomId: 'room_tmp' });
    await client.next();
    const viewer = connect(mock.port, '/ws/streaming/str_tmp?token=v');
    await viewer.next();
    assert.deepEqual(mock.stats(), { rooms: 1, streams: 1 });
    client.socket.close();
    viewer.socket.close();
    await eventually(() => mock.stats().rooms === 0 && mock.stats().streams === 0);
  });

  await t.test('check-in y mosaico ignoran los frames del cliente', async () => {
    for (const [path, welcome] of [
      ['/ws/checkin/evt_9?token=t', 2],
      ['/ws/mosaic/evt_9?token=t', 1],
    ]) {
      const client = connect(mock.port, path);
      for (let i = 0; i < welcome; i++) await client.next();
      client.send({ type: 'PING' });
      client.send({ type: 'CHECK_IN', userId: 'usr_falso' });
      assert.ok(await client.nothingWithin(), path);
      client.socket.close();
    }
  });

  await t.test('check-in: al conectar envía STATS_UPDATE y CHECK_IN del evento', async () => {
    const client = connect(mock.port, '/ws/checkin/evt_42?token=t');
    const stats = await client.next();
    const checkIn = await client.next();
    assert.equal(stats.type, 'STATS_UPDATE');
    assert.equal(stats.data.eventId, 'evt_42');
    assert.equal(checkIn.type, 'CHECK_IN');
    assert.equal(checkIn.data.eventId, 'evt_42');
    client.socket.close();
  });

  await t.test('mosaico: al conectar envía NEW_ITEM sin `data`', async () => {
    const client = connect(mock.port, '/ws/mosaic/evt_42?token=t');
    const frame = await client.next();
    assert.equal(frame.type, 'NEW_ITEM');
    assert.equal(frame.item.eventId, 'evt_42');
    client.socket.close();
  });

  await t.test('streaming: VIEWER_COUNT y REACTION a todos con la identidad del mock', async () => {
    const host = connect(mock.port, '/ws/streaming/str_1?token=h');
    assert.deepEqual(await host.next(), { type: 'VIEWER_COUNT', data: { count: 1 } });
    const viewer = connect(mock.port, '/ws/streaming/str_1?token=v');
    assert.deepEqual(await viewer.next(), { type: 'VIEWER_COUNT', data: { count: 2 } });
    assert.deepEqual(await host.next(), { type: 'VIEWER_COUNT', data: { count: 2 } });

    viewer.send({ type: 'REACTION', data: { type: 'HEART', x: 0.5, y: 0.5 } });
    const ids = [];
    for (const client of [host, viewer]) {
      const frame = await client.next();
      assert.equal(frame.type, 'REACTION');
      assert.equal(frame.data.type, 'HEART');
      assert.equal(frame.data.streamId, 'str_1');
      assert.equal(frame.data.userId, MOCK_USER.userId);
      assert.equal(frame.data.x, 0.5);
      ids.push(frame.data.id);
    }
    assert.equal(ids[0], ids[1]);

    // Sin x/y no llegan los del fixture, y cada reacción tiene su id.
    viewer.send({ type: 'REACTION', data: { type: 'FIRE' } });
    for (const client of [host, viewer]) {
      const frame = await client.next();
      assert.equal(frame.data.type, 'FIRE');
      assert.equal('x' in frame.data, false);
      assert.equal('y' in frame.data, false);
      assert.notEqual(frame.data.id, ids[0]);
    }

    // La identidad del cliente tampoco vale en streaming.
    viewer.send({ type: 'REACTION', data: { type: 'HEART', userId: 'usr_falso' } });
    const rejected = await viewer.next();
    assert.equal(rejected.type, 'ERROR');
    assert.equal(rejected.data.error, 'VALIDATION_ERROR');
    assert.ok(await host.nothingWithin());
    viewer.socket.close();
    assert.deepEqual(await host.next(), { type: 'VIEWER_COUNT', data: { count: 1 } });
    host.socket.close();
  });
});
