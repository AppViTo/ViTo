#!/usr/bin/env node
// Mock WebSocket de docs/api/asyncapi.yaml (T-C05, ADR-006): mismo protocolo que el
// gateway en el puerto 4011, para que la app pruebe chat, check-in, mosaico y
// streaming sin backend. Los frames salen de docs/api/fixtures/ws/ (un fixture por
// mensaje del contrato), así que el mock no puede contradecir al contrato.
//
// Comportamiento por canal:
//   - Todos: sin `?token=` cierra con 4401 (como el gateway).
//   - Chat y streaming: un frame del cliente que no valida contra `publish` (con los
//     objetos cerrados, así que la identidad del cliente no valida) responde
//     ERROR `VALIDATION_ERROR` sin cerrar.
//   - /ws/chat: JOIN_ROOM → ROOM_JOINED; LEAVE_ROOM → ROOM_LEFT; MARK_READ →
//     MESSAGES_READ; SEND_MESSAGE → MESSAGE a todos los que entraron a la sala;
//     TYPING → TYPING a los demás de la sala. SEND_MESSAGE, TYPING y MARK_READ sin
//     haber entrado a la sala responden ERROR `FORBIDDEN`.
//   - /ws/checkin/{eventId}: al conectar, STATS_UPDATE y CHECK_IN del evento.
//   - /ws/mosaic/{eventId}: al conectar, NEW_ITEM del evento.
//     Check-in y mosaico no tienen `publish`: los frames del cliente se ignoran.
//   - /ws/streaming/{streamId}: VIEWER_COUNT al entrar y salir; REACTION → REACTION
//     a todos los conectados al stream, con la identidad del mock; PING → PONG
//     (el latido solo existe en este canal).
//   - Con --demo <segundos> repite los frames de bienvenida en ese intervalo.
//
// Uso: npm run mock:ws  [-- --port 4011 --demo 5]

import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parse as parseYaml } from 'yaml';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { WebSocketServer } from 'ws';
import { closed } from './asyncapi-schema.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SPEC = join(ROOT, 'docs/api/asyncapi.yaml');
const FIXTURES = join(ROOT, 'docs/api/fixtures/ws');

/** Identidad con la que el mock firma lo que "envía" el usuario conectado. */
export const MOCK_USER = Object.freeze({
  userId: 'usr_mock',
  displayName: 'Usuario de prueba',
});

/** Fixtures de frames por `messageId`. */
export function loadFrames(dir = FIXTURES) {
  const frames = {};
  for (const file of readdirSync(dir)) {
    if (file.endsWith('.json')) {
      frames[basename(file, '.json')] = JSON.parse(readFileSync(join(dir, file), 'utf8'));
    }
  }
  return frames;
}

/** Valida frames del cliente contra los mensajes `publish` de cada canal. */
function buildClientValidators(specPath = SPEC) {
  const doc = parseYaml(readFileSync(specPath, 'utf8'));
  // Objetos cerrados, como en validate-asyncapi.mjs: un campo que el contrato no
  // declara (la identidad del cliente, ADR-015) hace fallar el frame.
  const defs = closed(doc.components?.schemas ?? {});
  const ajv = new Ajv2020({ strict: false, allErrors: true });
  addFormats(ajv);
  const validators = {};
  for (const [channel, item] of Object.entries(doc.channels ?? {})) {
    const refs = item.publish?.message?.oneOf ?? (item.publish?.message ? [item.publish.message] : []);
    validators[channel] = refs.map((ref) => {
      const name = ref.$ref.split('/').pop();
      const payload = closed(doc.components.messages[name].payload);
      return { name, validate: ajv.compile({ ...payload, $defs: defs }) };
    });
  }
  return validators;
}

/** Canal de asyncapi.yaml para una ruta, o `null`. */
export function matchChannel(pathname) {
  if (pathname === '/ws/chat') return { channel: '/ws/chat' };
  const match = /^\/ws\/(checkin|mosaic|streaming)\/([^/]+)$/.exec(pathname);
  if (!match) return null;
  const [, kind, id] = match;
  const param = kind === 'streaming' ? 'streamId' : 'eventId';
  return { channel: `/ws/${kind}/{${param}}`, id };
}

const now = () => new Date().toISOString().replace(/\.\d{3}Z$/, 'Z');
const send = (socket, frame) => {
  if (socket.readyState === socket.OPEN) socket.send(JSON.stringify(frame));
};

/**
 * Arranca el mock. Devuelve `{ port, close() }`.
 * @param {{ port?: number, host?: string, demoSeconds?: number, log?: (msg: string) => void }} options
 */
export async function startMockWs({
  port = 4011,
  host = '0.0.0.0',
  demoSeconds = 0,
  log = () => {},
} = {}) {
  const frames = loadFrames();
  const validators = buildClientValidators();
  const rooms = new Map(); // roomId -> Set<socket>
  const streams = new Map(); // streamId -> Set<socket>
  const timers = new Set();
  let sequence = 0;
  /** Id único del mock (`msg_mock_1`, `rct_mock_2`…). */
  const nextId = (prefix) => `${prefix}_mock_${++sequence}`;

  const errorFrame = (error, message) => ({ type: 'ERROR', data: { error, message } });
  const notInRoom = () => errorFrame('FORBIDDEN', 'Primero entra a la sala con JOIN_ROOM');

  /** Quita [socket] de [groups] y borra los grupos que quedan vacíos. */
  const leave = (groups, key, socket) => {
    const members = groups.get(key);
    if (!members) return;
    members.delete(socket);
    if (members.size === 0) groups.delete(key);
  };

  const welcome = {
    '/ws/checkin/{eventId}': (id) => [
      { ...frames.CheckInStatsUpdate, data: { ...frames.CheckInStatsUpdate.data, eventId: id } },
      {
        ...frames.CheckInRegistered,
        data: { ...frames.CheckInRegistered.data, eventId: id, checkedInAt: now() },
      },
    ],
    '/ws/mosaic/{eventId}': (id) => [
      { ...frames.MosaicNewItem, item: { ...frames.MosaicNewItem.item, eventId: id, timestamp: now() } },
    ],
    '/ws/streaming/{streamId}': () => [],
    '/ws/chat': () => [],
  };

  const viewerCount = (streamId) => {
    const sockets = streams.get(streamId) ?? new Set();
    for (const socket of sockets) {
      send(socket, { type: 'VIEWER_COUNT', data: { count: sockets.size } });
    }
  };

  const handleChat = (socket, frame) => {
    const roomId = frame.roomId;
    if (frame.type === 'JOIN_ROOM') {
      if (!rooms.has(roomId)) rooms.set(roomId, new Set());
      rooms.get(roomId).add(socket);
      send(socket, { type: 'ROOM_JOINED', data: { roomId } });
      return;
    }
    if (frame.type === 'LEAVE_ROOM') {
      // Idempotente: salir de una sala en la que no se estaba también responde.
      leave(rooms, roomId, socket);
      send(socket, { type: 'ROOM_LEFT', data: { roomId } });
      return;
    }
    // El resto exige haber entrado a la sala.
    const members = rooms.get(roomId);
    if (!members?.has(socket)) {
      send(socket, notInRoom());
      return;
    }
    switch (frame.type) {
      case 'MARK_READ':
        send(socket, { type: 'MESSAGES_READ', data: { roomId } });
        return;
      case 'TYPING':
        for (const other of members) {
          if (other !== socket) {
            send(other, {
              type: 'TYPING',
              data: { roomId, userId: MOCK_USER.userId, displayName: MOCK_USER.displayName, isTyping: frame.isTyping },
            });
          }
        }
        return;
      case 'SEND_MESSAGE': {
        const message = {
          id: nextId('msg'),
          roomId,
          senderId: MOCK_USER.userId,
          senderName: MOCK_USER.displayName,
          content: frame.content,
          type: frame.messageType ?? 'TEXT',
          mediaUrl: frame.mediaUrl ?? null,
          timestamp: now(),
          deleted: false,
        };
        for (const member of members) send(member, { type: 'MESSAGE', data: message });
        return;
      }
      default:
    }
  };

  const handleStreaming = (socket, streamId, frame) => {
    if (frame.type === 'PING') {
      send(socket, { type: 'PONG' });
      return;
    }
    if (frame.type !== 'REACTION') return;
    // Solo lo que mandó el cliente más lo que pone el servicio: nada del fixture.
    const { type, emoji, x, y } = frame.data;
    const reaction = {
      id: nextId('rct'),
      streamId,
      userId: MOCK_USER.userId,
      displayName: MOCK_USER.displayName,
      avatar: null,
      type,
      emoji: emoji ?? null,
      ...(x !== undefined ? { x } : {}),
      ...(y !== undefined ? { y } : {}),
      timestamp: now(),
    };
    for (const viewer of streams.get(streamId) ?? []) {
      send(viewer, { type: 'REACTION', data: reaction });
    }
  };

  const server = new WebSocketServer({ port, host });
  await new Promise((resolve, reject) => {
    server.once('listening', resolve);
    server.once('error', reject);
  });

  server.on('connection', (socket, request) => {
    const url = new URL(request.url, 'ws://mock');
    const route = matchChannel(url.pathname);
    if (!route) {
      socket.close(4404, 'Ruta WS no definida en asyncapi.yaml');
      return;
    }
    if (!url.searchParams.get('token')) {
      socket.close(4401, 'Token ausente o inválido');
      return;
    }
    const { channel, id } = route;
    log(`conexión ${url.pathname}`);

    const sendWelcome = () => welcome[channel](id).forEach((frame) => send(socket, frame));
    sendWelcome();
    if (demoSeconds > 0) {
      const timer = setInterval(sendWelcome, demoSeconds * 1000);
      timers.add(timer);
      socket.on('close', () => {
        clearInterval(timer);
        timers.delete(timer);
      });
    }

    if (channel === '/ws/streaming/{streamId}') {
      if (!streams.has(id)) streams.set(id, new Set());
      streams.get(id).add(socket);
      viewerCount(id);
    }

    socket.on('message', (raw) => {
      let frame;
      try {
        frame = JSON.parse(raw.toString());
      } catch {
        if (validators[channel]?.length) {
          send(socket, errorFrame('VALIDATION_ERROR', 'El frame no es JSON válido'));
        }
        return;
      }
      const channelValidators = validators[channel] ?? [];
      // Check-in y mosaico no tienen `publish` (ni `WsError`): solo envía el servidor.
      if (channelValidators.length === 0) return;
      const accepted = channelValidators.some(({ validate }) => validate(frame));
      if (!accepted) {
        send(
          socket,
          errorFrame('VALIDATION_ERROR', `Frame no válido para ${channel} según asyncapi.yaml`),
        );
        return;
      }
      if (channel === '/ws/chat') handleChat(socket, frame);
      if (channel === '/ws/streaming/{streamId}') handleStreaming(socket, id, frame);
    });

    socket.on('close', () => {
      for (const roomId of [...rooms.keys()]) leave(rooms, roomId, socket);
      if (channel === '/ws/streaming/{streamId}') {
        leave(streams, id, socket);
        viewerCount(id);
      }
    });
  });

  return {
    port: server.address().port,
    /** Salas y streams con alguien conectado (para las pruebas). */
    stats: () => ({ rooms: rooms.size, streams: streams.size }),
    close: () =>
      new Promise((resolve) => {
        for (const timer of timers) clearInterval(timer);
        for (const client of server.clients) client.terminate();
        server.close(() => resolve());
      }),
  };
}

function argValue(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  const port = Number(argValue('port', process.env.MOCK_WS_PORT ?? 4011));
  const demoSeconds = Number(argValue('demo', 0));
  const mock = await startMockWs({ port, demoSeconds, log: (msg) => console.log(`[mock:ws] ${msg}`) });
  console.log(
    `[mock:ws] escuchando en ws://localhost:${mock.port}/ws/... (asyncapi.yaml v0)` +
      (demoSeconds > 0 ? ` · demo cada ${demoSeconds}s` : ''),
  );
}
