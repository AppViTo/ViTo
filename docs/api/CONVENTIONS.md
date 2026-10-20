# CONVENTIONS — Convenciones del contrato de API ViTo

**Este documento es vinculante para ambos equipos.** Define el "lenguaje" que comparten el frontend Flutter y los servicios backend. Todo lo que no esté cubierto aquí se decide mediante RFC (ver `CONTRIBUTING.md`) y, si es una decisión relevante, se registra como ADR en `docs/decisions/`.

- Contrato REST: [`docs/api/openapi.yaml`](./openapi.yaml)
- Contrato realtime: [`docs/api/asyncapi.yaml`](./asyncapi.yaml)
- Fixtures ejecutables: [`docs/api/fixtures/`](./fixtures/)

---

## 1. Base URL y versionado

| Entorno | URL |
|---------|-----|
| Gateway (única puerta de entrada) | `http://localhost:8080/api/v1` |
| Mock REST (Prism) | `http://localhost:4010/api/v1` |
| Producción | `https://api.vito.com/api/v1` |

- **Toda** la comunicación REST pasa por el gateway. Nunca se llama directo a un servicio (8081–8089, 8000–8002).
- Versionado en path: `/api/v1/<recurso>`. Un cambio incompatiblemente mayor ⇒ `/api/v2`.
- Los WebSockets se conectan a `Env.wsBaseUrl` (ver §7).

---

## 2. Autenticación y contexto de usuario

### 2.1 Título
```
Authorization: Bearer <accessToken>
```
- Emitido por `POST /api/v1/auth/register|login|refresh`.
- `refreshToken` viaja solo en el cuerpo del request de refresh.
- **Nunca** se exige `Authorization` en `register`, `login`, `refresh`, `logout` ni en endpoints públicos.

### 2.2 Headers de contexto inyectados por el gateway
El gateway valida el JWT y **reescribe** el request añadiendo:

| Header | Ejemplo | Origen |
|--------|---------|--------|
| `X-User-Id` | `usr_01H8...` | claim `sub` |
| `X-User-Email` | `alex@vito.com` | claim `email` |
| `X-User-Roles` | `USER,ORGANIZER` | claim `roles` |

Reglas:

- **Obligatorio para los servicios.** `chat-service` y `media-service` toman la identidad de `@RequestHeader("X-User-Id")`; si llega vacío devuelven 400. Por eso **solo funcionan a través del gateway**.
- Estos headers **nunca** se aceptan del cliente. El gateway los elimina del request entrante antes de reenviarlos (header spoofing).
- Las llamadas directas a un servicio (tests, scripts) deben simularlos: `curl -H "X-User-Id: usr_1" -H "X-User-Email: a@b.c"`.

---

## 3. Envelopes (formatos de respuesta)

### 3.1 Objetos individuales → objeto plano
```json
{ "id": "evt_01H8...", "title": "Boda Andrea & Luis" }
```
Sin envoltorio `data`. Sin `result`.

### 3.2 Listas → **array plano**
```json
[ { "id": "evt_1" }, { "id": "evt_2" } ]
```
> **Decisión frontend (ADR-002):** el backend devuelve `List<T>` directamente, **no** `{ "items": [...], "total": N }`. El frontend parsea `(response.data as List)`.

Consecuencia: **no existe `total` en la respuesta**. Si una pantalla necesita el total de páginas, se expone explícitamente como campo propio de ese endpoint, no como envelope genérico.

### 3.3 Errores → envelope único
```json
{
  "error": "VALIDATION_ERROR",
  "message": "El campo title es obligatorio",
  "status": 400,
  "timestamp": "2026-09-25T14:03:22Z"
}
```

| Campo | Tipo | Obligatorio | Descripción |
|-------|------|-------------|-------------|
| `error` | string | sí | Código de máquina en `SCREAMING_SNAKE_CASE` |
| `message` | string | sí | Mensaje legible para humanos (es-ES) |
| `status` | int | sí | Código HTTP, espejo del status line |
| `timestamp` | string | sí | ISO-8601 UTC con `Z` |
| `details` | object | no | Solo en validación: `{"field": "reason"}` |

El frontend lee `response.data['message']` para mostrar el error al usuario ⇒ **`message` es obligatorio en toda respuesta 4xx/5xx**.

Códigos de error canónicos: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `INTERNAL_ERROR`, `SERVICE_UNAVAILABLE`.

---

## 4. Códigos HTTP

| Acción | Éxito | Notas |
|--------|-------|-------|
| Crear recurso | `201 Created` | `POST` que crea. Ver ADR-005 sobre `POST /events`. |
| Leer / actualizar / acción sin cuerpo | `200 OK` | |
| Borrado lógico o sin contenido | `204 No Content` | `DELETE` que no devuelve body |
| Validación fallida | `400` | nunca `409` |
| No autenticado | `401` | sin `Authorization` o token inválido |
| Sin permiso | `403` | |
| No existe | `404` | |
| Recurso duplicado | `409` | ej. usuario ya registrado — **ver ADR-009** |
| Límite de tasa | `429` + header `Retry-After` | |
| Error servidor | `500` | |

**Regla:** la validación de datos ⇒ `400`. La colisión de unicidad ⇒ `409`.

---

## 5. Paginación

- Query params en **todas** las listas: `?page=0&size=20`
  - `page` base **0**, default `0`
  - `size` default `20`, máximo `100`
  - Un endpoint sin `page`/`size` es un error de contrato (a menosque el ADR diga lo contrario).
- Orden por defecto: `createdAt` descendente.
- La respuesta es un array plano (§3.2), por lo que el cliente pagina con `page`/`size` y no con `total`.

---

## 6. Tipos de datos

| Concepto | Formato | Ejemplo |
|----------|---------|---------|
| Identificador | `string` prefijado por tipo | `evt_01H8X...`, `usr_01H8...` |
| Fecha/hora | ISO-8601 **UTC** con `Z` | `2026-09-25T14:03:22Z` |
| Fecha sin hora | `YYYY-MM-DD` | `2026-10-12` |
| Moneda | entero, unidad mínima | centavos |
| Coordenaadas | `double`, orden `[lng, lat]` cuando sea array; `latitude`/`longitude` cuando sea objeto | `-74.0721, 4.7110` |
| Boolean | `true`/`false` | nunca strings |
| Nulos | campo presente en `null` **o** ausente | ambos aceptados |

### Enums (valores exactos — el frontend hace `switch` exhaustivo)

| Entorno | Valores |
|---------|---------|
| `Event.status` | `DRAFT` `PUBLISHED` `CANCELLED` `COMPLETED` |
| `Rsvp.status` | `PENDING` `ACCEPTED` `DECLINED` `MAYBE` `WAITLISTED` |
| `Invitation.status` | `PENDING` `ACCEPTED` `DECLINED` `EXPIRED` |
| `CheckInTicket.status` | `PENDING` `ACTIVE` `USED` `EXPIRED` `REVOKED` |
| `MosaicFeedItem.type` | `CHECK_IN` `POST` `PHOTO` `REACTION` `STORY` `LIVE_UPDATE` `ANNOUNCEMENT` |
| `LiveStream.status` | `IDLE` `CONNECTING` `LIVE` `BUFFERING` `ENDED` `ERROR` |
| `Reaction.type` | `LIKE` `HEART` `FIRE` `CLAP` `CELEBRATION` `CUSTOM` |
| `StreamChatMessage.type` | `NORMAL` `SUPER_CHAT` `SYSTEM` `JOIN` `LEAVE` |
| `ChatRoom.type` | `EVENT_CHAT` `PRIVATE` `GROUP` |
| `ChatMessage.type` | `TEXT` `IMAGE` `VIDEO` `AUDIO` `FILE` `SYSTEM` `TYPING` `READ_RECEIPT` |
| `EventArchive.status` | `PENDING` `PROCESSING` `COMPLETED` `FAILED` |
| `ArchiveMedia.type` | `PHOTO` `VIDEO` `STREAM_RECORDING` `LIVE_SCREENSHOT` |

> Un valor fuera de esta lista hace que el frontend clasifique el recurso como el caso por defecto (p.ej. un `Event.status` desconocido se trata como `DRAFT`). **Añadir un valor exige RFC.**

### Estructura del Evento (ADR-001)
```json
{
  "id": "evt_1",
  "organizerId": "usr_1",
  "title": "Boda Andrea & Luis",
  "description": "...",
  "details": {
    "startDate": "2026-10-12T18:00:00Z",
    "endDate": "2026-10-13T02:00:00Z",
    "location": "Hacienda El Retiro",
    "address": "Km 5 vía Chía",
    "latitude": 4.7110,
    "longitude": -74.0721,
    "coverImages": ["https://..."],
    "tags": ["boda"],
    "category": "WEDDING"
  },
  "settings": {
    "isPublic": true,
    "requiresApproval": false,
    "maxAttendees": 150,
    "allowGuestInvites": true,
    "enableLiveStreaming": true,
    "enableMosaic": true,
    "hideAddressUntilAccepted": false
  },
  "stats": {
    "totalInvited": 120, "totalAccepted": 96, "totalDeclined": 4,
    "totalMaybe": 10, "totalWaitlisted": 3, "totalCheckedIn": 80,
    "totalViewers": 0
  },
  "status": "PUBLISHED",
  "createdAt": "2026-09-01T10:00:00Z",
  "updatedAt": "2026-09-01T10:00:00Z"
}
```
No existen `name`, `date`, `endDate`, `isPublic`, `maxAttendees`, `currentAttendees`, `category` **en la raíz**. Todo vive dentro de `details` / `settings` / `stats`.

---

## 7. WebSockets / realtime

- **Protocolo: WebSocket crudo** (`web_socket_channel` en Flutter). **No STOMP, no SockJS.** Ver ADR-006.
- Todas las rutas WS se exponen **a través del gateway** (`ws://localhost:8080/ws/...`). El gateway debe tener ruta `/ws/**`; sin ella el handshake da 404 (RFC pendiente del equipo backend).
- Autenticación: query param `?token=<accessToken>` en el handshake. El gateway lo valida y rechaza con `4401` si es inválido.
- El cliente conecta con `Env.wsBaseUrl`; **nunca** se hardcodea un host o puerto.
- **El cliente nunca envía su identidad** (`userId`, `senderId`) ni en la query ni en los frames: el servicio la toma de `X-User-Id`, que inyecta el gateway al validar el token (ADR-007, ADR-015).

| Recurso | Ruta |
|---------|------|
| Chat | `/ws/chat?token=<jwt>` |
| Check-in | `/ws/checkin/{eventId}?token=<jwt>` |
| Mosaico | `/ws/mosaic/{eventId}?token=<jwt>` |
| Streaming | `/ws/streaming/{streamId}?token=<jwt>` |

### Formato de frame

**Servidor → cliente**
```json
{ "type": "NEW_ITEM", "data": { } }
```
`type` en `SCREAMING_SNAKE_CASE`. El cliente hace `switch(type)`.

**Cliente → servidor**
```json
{ "type": "SEND_MESSAGE", "roomId": "room_1", "content": "hola" }
```

Frames por recurso: ver `asyncapi.yaml` y los handoffs (`docs/handoff/`).

---

## 8. Subida de archivos

- `POST /api/v1/media/upload` con `multipart/form-data`: campo `file` (binario) + `eventId`.
- Respuesta `201` con `Media { id, url, thumbnailUrl, mimeType, size, ... }`.
- Tamaño máximo **10 MB** por archivo; formatos: `image/jpeg`, `image/png`, `image/webp`, `video/mp4`.
- El flujo del mosaico es: subir a media → usar la URL devuelta en `POST /mosaic/photo`.

---

## 9. Rate limiting

| Servicio | Requests/s | Burst |
|----------|-----------|-------|
| auth | 10 | 20 |
| events | 20 | 40 |
| resto | 10 | 20 |

Al superarlo: `429` con el envelope de error §3.3 y header `Retry-After: <segundos>`.

---

## 10. Contrato ejecutable (cómo se verifica que no hay divergencia)

| Artefacto | Quién lo usa | Herramienta |
|-----------|--------------|-------------|
| `openapi.yaml` | ambos | `npx @stoplight/spectral-cli lint docs/api/openapi.yaml` |
| `asyncapi.yaml` | ambos | `npx @asyncapi/cli validate docs/api/asyncapi.yaml` |
| `docs/api/fixtures/**/*.json` | frontend (tests Dart) y mocks (Prism/Node) | `flutter test` |
| Mock REST | frontend sin backend | `npm run mock:api` → Prism :4010 |
| Mock WS | frontend sin backend | `npm run mock:ws` → :4011 |
| `schemathesis` | backend | `schemathesis run --checks all <spec>` |

**Un solo origen de verdad:** los fixtures alimentan el mock *y* el test de serialización Dart. Si cambia el contrato, cambian los fixtures, y ambos lados se enteran en CI.
