# ADR-006: WebSocket crudo, no STOMP

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), aprobada por ambos leads
- **Nota:** única decisión técnica de la ronda que no era evidente desde el frontend; se adopta la opción que respeta "manda el frontend" y que minimiza reescrituras en ambos lados.

## Contexto

- El frontend usa `web_socket_channel` **crudo** en 4 repositorios (`chat`, `checkin`, `mosaic`, `streaming`): envía y recibe JSON plano con `{type, data}`. No hay dependencia STOMP ni SockJS.
- El backend `chat-service` expone **STOMP** (`WebSocketConfig.java` → endpoint `/ws/chat` con SockJS, broker `/topic`, prefijo `/app`) además de un `ChatWebSocketHandler` *raw* que **no está registrado en ningún sitio** (código muerto).
- `checkin-service` sí registra un handler **raw** en `/ws/checkin/{eventId}`.
- `mosaic-service` y `streaming-service` tienen handlers **raw sin registrar** (no hay `WebSocketConfig`).
- El gateway **no tiene ruta `/ws/**`**: sus rutas son `/api/v1/{service}/**`. Como el frontend conecta contra `ws://localhost:8080`, el handshake daría **404 hoy**.

## Decisión

1. **Protocolo: WebSocket crudo con frames JSON** `{ "type": "...", "data": { } }`. Sin STOMP, sin SockJS.
2. **Rutas** (publicadas en `docs/api/asyncapi.yaml`):

   | Recurso | Ruta |
   |---------|------|
   | Chat | `/ws/chat?token=<jwt>` (ver ADR-015) |
   | Check-in | `/ws/checkin/{eventId}?token=<jwt>` |
   | Mosaico | `/ws/mosaic/{eventId}?token=<jwt>` |
   | Streaming | `/ws/streaming/{streamId}?token=<jwt>` |

3. **El gateway expone `/ws/**`** y hace proxy hacia el servicio correspondiente, propagando el handshake y los frames (o `WebSocketHandler` en Spring Cloud Gateway). **RFC a cargo del equipo backend** — es prerrequisito para que cualquier pantalla realtime funcione.
4. **Autenticación por query param `?token=`**, validada por el gateway en el handshake. Token inválido ⇒ cierre con código `4401`.
5. En `chat-service`, **se elimina la configuración STOMP** y se registra el `ChatWebSocketHandler` raw ya existente (hoy muerto) — menos código que mantener que dos stack paralelos.

## Consecuencias

- **Backend:** 4 servicios registran su handler raw; `chat-service` retira STOMP/SockJS (dependencia `spring-boot-starter-websocket` se queda, la config STOMP se va); gateway añade rutas `/ws/**`.
- **Frontend:** sin cambios de código — solo `Env.wsBaseUrl` (se quitan los hosts hardcodeados `localhost:8086`).
- `asyncapi.yaml` documenta los 4 canales y sus frames.
- Los mocks WS (`npm run mock:ws`, puerto 4011) replican el mismo protocolo.

## Alternativas consideradas

| Alternativa | Por qué se descarta |
|-------------|---------------------|
| **Migrar el frontend a STOMP** (`stomp_dart_client`) | Reescribe los 4 repositorios realtime + sus blocs. Además habría que añadir SockJS al cliente o forzar WebSocket puro. |
| **Dejar STOMP en chat y raw en el resto** | Dos protocolos distintos en la misma app; el cliente tendría dos stacks. |
| **`/ws` por servicio (8088, 8084…) sin gateway** | Rompe la regla "todo pasa por el gateway" y obliga a CORS/CORS-equivalente en WS por puerto, además de hardcodear puertos en el cliente. |

## Actualizaciones

### 2026-10-20 — el chat deja de llevar `?userId=` (ADR-015)

La ruta original del chat era `/ws/chat?userId=<id>&token=<jwt>`. El `userId` lo elegía el
cliente y permitía suplantar a otro usuario. [ADR-015](./adr-015-chat-ws-identity.md) lo
retira: la ruta queda `/ws/chat?token=<jwt>` y el servicio toma la identidad de
`X-User-Id` (ADR-007).
