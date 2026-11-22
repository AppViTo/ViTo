#!/usr/bin/env node
// Mock WebSocket de docs/api/asyncapi.yaml (T-C05, ADR-006): mismo protocolo que el
// gateway en el puerto 4011, para que la app pruebe chat, check-in, mosaico y
// streaming sin backend. Los frames salen de docs/api/fixtures/ws/ (un fixture por
// mensaje del contrato), así que el mock no puede contradecir al contrato.
//
// Comportamiento por canal:
//   - Todos: sin `?token=` cierra con 4401 (como el gateway). PING → PONG. Un frame
//     del cliente que no valida contra `publish` responde ERROR sin cerrar.
//   - /ws/chat: JOIN_ROOM → ROOM_JOINED; LEAVE_ROOM → ROOM_LEFT; MARK_READ →
//     MESSAGES_READ; SEND_MESSAGE → MESSAGE a todos los que entraron a la sala;
//     TYPING → TYPING a los demás de la sala.
//   - /ws/checkin/{eventId}: al conectar, STATS_UPDATE y CHECK_IN del evento.
//   - /ws/mosaic/{eventId}: al conectar, NEW_ITEM del evento.
//   - /ws/streaming/{streamId}: VIEWER_COUNT al entrar y salir; REACTION → REACTION
//     a todos los conectados al stream, con la identidad del mock.
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
  const defs = JSON.parse(
    JSON.stringify(doc.components?.schemas ?? {}).replaceAll('#/components/schemas/', '#/$defs/'),
  );
  const ajv = new Ajv2020({ strict: false, allErrors: true });
  addFormats(ajv);
  const validators = {};
  for (const [channel, item] of Object.entries(doc.channels ?? {})) {
    const refs = item.publish?.message?.oneOf ?? (item.publish?.message ? [item.publish.message] : []);
    validators[channel] = refs.map((ref) => {
      const name = ref.$ref.split('/').pop();
      const payload = JSON.parse(
        JSON.stringify(doc.components.messages[name].payload).replaceAll(
          '#/components/schemas/',
          '#/$defs/',
        ),
      );
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

  const errorFrame = (message) => ({
    type: 'ERROR',
    data: { error: 'VALIDATION_ERROR', message },
  });

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
    switch (frame.type) {
      case 'JOIN_ROOM':
        if (!rooms.has(roomId)) rooms.set(roomId, new Set());
        rooms.get(roomId).add(socket);
        send(socket, { type: 'ROOM_JOINED', data: { roomId } });
        return;
      case 'LEAVE_ROOM':
        rooms.get(roomId)?.delete(socket);
        send(socket, { type: 'ROOM_LEFT', data: { roomId } });
        return;
      case 'MARK_READ':
        send(socket, { type: 'MESSAGES_READ', data: { roomId } });
        return;
      case 'TYPING':
        for (const other of rooms.get(roomId) ?? []) {
          if (other !== socket) {
            send(other, {
              type: 'TYPING',
              data: { roomId, userId: MOCK_USER.userId, displayName: MOCK_USER.displayName, isTyping: frame.isTyping },
            });
          }
        }
        return;
      case 'SEND_MESSAGE': {
        const members = rooms.get(roomId);
        if (!members?.has(socket)) {
          send(socket, errorFrame('Primero entra a la sala con JOIN_ROOM'));
          return;
        }
        const message = {
          ...frames.ChatMessageReceived.data,
          id: `msg_mock_${Date.now()}`,
          roomId,
          senderId: MOCK_USER.userId,
          senderName: MOCK_USER.displayName,
          content: frame.content,
          type: frame.messageType ?? 'TEXT',
          mediaUrl: frame.mediaUrl ?? null,
          timestamp: now(),
        };
        for (const member of members) send(member, { type: 'MESSAGE', data: message });
        return;
      }
      default:
    }
  };

  const handleStreaming = (socket, streamId, frame) => {
    if (frame.type !== 'REACTION') return;
    const reaction = {
      ...frames.StreamReactionReceived.data,
      ...frame.data,
      id: `rct_mock_${Date.now()}`,
      streamId,
      userId: MOCK_USER.userId,
      displayName: MOCK_USER.displayName,
      avatar: null,
      emoji: frame.data.emoji ?? null,
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
        send(socket, errorFrame('El frame no es JSON válido'));
        return;
      }
      if (frame?.type === 'PING') {
        send(socket, { type: 'PONG' });
        return;
      }
      const accepted = (validators[channel] ?? []).some(({ validate }) => validate(frame));
      if (!accepted) {
        send(socket, errorFrame(`Frame no válido para ${channel} según asyncapi.yaml`));
        return;
      }
      if (channel === '/ws/chat') handleChat(socket, frame);
      if (channel === '/ws/streaming/{streamId}') handleStreaming(socket, id, frame);
    });

    socket.on('close', () => {
      for (const members of rooms.values()) members.delete(socket);
      if (channel === '/ws/streaming/{streamId}') {
        streams.get(id)?.delete(socket);
        viewerCount(id);
      }
    });
  });

  return {
    port: server.address().port,
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
