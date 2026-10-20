# Inventario de endpoints — código vs contrato

> Generado del código el 2026-10-16 (rama `docs/constitution-v1`, commit `1e80ac4`).
> **100 operaciones de negocio**: 87 Java + 13 FastAPI (sin contar `/health`, `/actuator/*`
> ni `/fallback/*`). En `openapi.yaml`: **15** (una de ellas, `GET /events`, no existe en el código). Total de filas: 101.
>
> La columna **Decisión propuesta** NO es vinculante hasta que los leads la aprueben por
> ola (D-08 en `decisions-pending.md`). Mientras tanto, según la constitución (Principio I),
> todo lo que no está en el contrato se considera **no publicado**.

## Leyenda

| Decisión | Significado |
|----------|-------------|
| `CONTRATO` | Ya está en `openapi.yaml`. Falta fixture/mock (Ola 0). |
| `PUBLICAR` | Entra al contrato en la ola indicada, tal cual o con ajustes a CONVENTIONS. |
| `FUSIONAR→x` | Se retira y su función la cubre `x` (ver D-01 / D-03). |
| `INTERNO` | Lo usa otro servicio o un consumidor Kafka; **no** se enruta en el gateway ni entra a `openapi.yaml`. |
| `DIFERIR` | Fuera del MVP. Se retira del gateway de producción; el frontend oculta la UI. |

Columna **Front**: ✔ = el código Flutter ya lo llama (`lib/**/datasources`, `repositories`).

## Auth — `auth-service` (:8081)

| Método | Ruta | Contrato | Front | Decisión propuesta | Ola |
|--------|------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/auth/register` | ✔ | ✔ | `CONTRATO` | 0 |
| POST | `/api/v1/auth/login` | ✔ | ✔ | `CONTRATO` | 0 |
| POST | `/api/v1/auth/refresh` | ✔ | ✔ | `CONTRATO` | 0 |
| POST | `/api/v1/auth/logout` | ✔ | ✔ | `CONTRATO` | 0 |
| GET | `/api/v1/auth/me` | | ✔ | `PUBLICAR` (auto-login) | 1 |
| GET | `/api/v1/auth/validate` | | | `INTERNO` (el gateway valida el JWT localmente) | — |

## Events — `event-service` (:8083)

| Método | Ruta | Contrato | Front | Decisión propuesta | Ola |
|--------|------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/events` | ✔ | ✔ | `CONTRATO` | 0 |
| GET | `/api/v1/events` | ✔ | ✔ | `CONTRATO` — **no implementado en backend** (O-26, D-03) | 0 |
| GET | `/api/v1/events/{eventId}` | ✔ | ✔ | `CONTRATO` | 0 |
| PUT | `/api/v1/events/{eventId}` | | | `PUBLICAR` (solo organizador) | 1 |
| DELETE | `/api/v1/events/{eventId}` | | ✔ | `PUBLICAR` (solo organizador) | 1 |
| GET | `/api/v1/events/my-events` | | | `FUSIONAR→GET /events?scope=mine` (D-03) | 0 |
| GET | `/api/v1/events/upcoming` | | ✔ | `FUSIONAR→GET /events?scope=upcoming` (D-03) | 0 |
| POST | `/api/v1/events/{eventId}/rsvp` | | ✔ | `PUBLICAR` (ACCEPTED/DECLINED/MAYBE/WAITLIST, cupo atómico) | 1 |
| GET | `/api/v1/events/{eventId}/attendees` | | ✔ | `PUBLICAR` (paginado) | 1 |
| POST | `/api/v1/events/{eventId}/invitations` | | ✔ | `PUBLICAR` (app + link externo) | 1 |
| GET | `/api/v1/events/{eventId}/invitations` | | ✔ | `PUBLICAR` (solo organizador) | 1 |
| POST | `/api/v1/events/{eventId}/invitations/respond` | | ✔ | `PUBLICAR` o `FUSIONAR→/rsvp` (D-01) | 1 |
| POST | `/api/v1/events/{eventId}/check-in` | | ✔ | `FUSIONAR→/checkin/validate` (D-01) | 2 |
| GET | `/api/v1/events/{eventId}/qr-code` | | ✔ | `FUSIONAR→/checkin/ticket` (D-01) | 2 |

## Check-in / QR — `checkin-service` (:8084)

| Método | Ruta | Contrato | Front | Decisión propuesta | Ola |
|--------|------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/checkin/ticket` | | ✔ | `PUBLICAR` (emite QR JWT RSA, ver O-07) | 2 |
| POST | `/api/v1/checkin/validate` | | ✔ | `PUBLICAR` (solo staff; < 200 ms p99) | 2 |
| GET | `/api/v1/checkin/ticket/{ticketId}` | | ✔ | `PUBLICAR` | 2 |
| GET | `/api/v1/checkin/event/{eventId}/tickets` | | ✔ | `PUBLICAR` (organizador, paginado) | 2 |
| GET | `/api/v1/checkin/event/{eventId}/recent` | | ✔ | `PUBLICAR` | 2 |
| GET | `/api/v1/checkin/event/{eventId}/stats` | | ✔ | `PUBLICAR` | 2 |
| DELETE | `/api/v1/checkin/ticket/{ticketId}` | | ✔ | `PUBLICAR` (revocar) | 2 |

## Social — `social-service` (:8082)

| Método | Ruta | Contrato | Front | Decisión propuesta | Ola |
|--------|------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/social/follow/{userId}` | | ✔ | `PUBLICAR` | 2 |
| DELETE | `/api/v1/social/follow/{userId}` | | ✔ | `PUBLICAR` | 2 |
| GET | `/api/v1/social/profile/{userId}` | | ✔ | `PUBLICAR` (incluir `isFollowing`) | 2 |
| GET | `/api/v1/social/followers/{userId}` | | ✔ | `PUBLICAR` (paginado) | 2 |
| GET | `/api/v1/social/following/{userId}` | | ✔ | `PUBLICAR` (paginado) | 2 |
| GET | `/api/v1/social/search` | | ✔ | `PUBLICAR` (reemplaza `MockApiService` de people) | 2 |
| GET | `/api/v1/social/is-following/{userId}` | | ✔ | `FUSIONAR→profile.isFollowing` | 2 |

## Media — `media-service` (:8089)

| Método | Ruta | Contrato | Front | Decisión propuesta | Ola |
|--------|------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/media/upload` | ✔ | ✔ | `CONTRATO` | 0 |
| GET | `/api/v1/media/{mediaId}` | ✔ | | `CONTRATO` | 0 |
| GET | `/api/v1/media/event/{eventId}` | | | `PUBLICAR` (paginado) | 3 |
| GET | `/api/v1/media/user` | | | `DIFERIR` | — |
| PUT | `/api/v1/media/{mediaId}` | | | `DIFERIR` | — |
| DELETE | `/api/v1/media/{mediaId}` | | | `PUBLICAR` (dueño/organizador) | 3 |
| GET | `/api/v1/media/{mediaId}/presigned-url` | | | `PUBLICAR` (Principio IX) | 3 |
| POST | `/api/v1/media/{mediaId}/process` | | | `INTERNO` (transcode vía Kafka) | — |

## Mosaico — `mosaic-service` (:8085)

| Método | Ruta | Contrato | Front | Decisión propuesta | Ola |
|--------|------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/mosaic/post` | | ✔ | `PUBLICAR` (con `mediaId`) | 3 |
| POST | `/api/v1/mosaic/photo` | | ✔ | `FUSIONAR→/media/upload + /mosaic/post` (D-01) | 3 |
| POST | `/api/v1/mosaic/checkin` | | ✔ | `INTERNO` (consumidor del evento de check-in) | — |
| GET | `/api/v1/mosaic/feed/{eventId}` | | ✔ | `PUBLICAR` (paginado) | 3 |
| GET | `/api/v1/mosaic/grid/{eventId}` | | ✔ | `PUBLICAR` | 3 |
| POST | `/api/v1/mosaic/grid/{eventId}/initialize` | | ✔ | `PUBLICAR` (solo organizador) | 3 |
| GET | `/api/v1/mosaic/item/{itemId}` | | ✔ | `PUBLICAR` | 3 |
| POST | `/api/v1/mosaic/item/{itemId}/like` | | ✔ | `PUBLICAR` | 3 |
| DELETE | `/api/v1/mosaic/item/{itemId}/like` | | ✔ | `PUBLICAR` | 3 |

## Streaming — `streaming-service` (:8086)

| Método | Ruta | Contrato | Front | Decisión propuesta | Ola |
|--------|------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/streaming` | | ✔ | `PUBLICAR` (solo organizador) | 4 |
| POST | `/api/v1/streaming/{streamId}/start` | | ✔ | `PUBLICAR` | 4 |
| POST | `/api/v1/streaming/{streamId}/stop` | | ✔ | `PUBLICAR` | 4 |
| GET | `/api/v1/streaming/{streamId}` | | ✔ | `PUBLICAR` | 4 |
| GET | `/api/v1/streaming/{streamId}/info` | | ✔ | `FUSIONAR→GET /streaming/{streamId}` | 4 |
| GET | `/api/v1/streaming/event/{eventId}` | | ✔ | `PUBLICAR` | 4 |
| GET | `/api/v1/streaming/live` | | ✔ | `PUBLICAR` (paginado) | 4 |
| POST | `/api/v1/streaming/{streamId}/reaction` | | ✔ | `PUBLICAR` (también como frame WS) | 4 |
| GET | `/api/v1/streaming/{streamId}/reactions` | | ✔ | `PUBLICAR` | 4 |
| POST | `/api/v1/streaming/{streamId}/chat` | | ✔ | `FUSIONAR→chat-service` (D-01) | — |
| GET | `/api/v1/streaming/{streamId}/chat` | | ✔ | `FUSIONAR→chat-service` (D-01) | — |

## Archivo — `archive-service` (:8087)

| Método | Ruta | Contrato | Front | Decisión propuesta | Ola |
|--------|------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/archive` | | ✔ | `PUBLICAR` (organizador, al cerrar evento) | 5 |
| GET | `/api/v1/archive/{archiveId}` | | ✔ | `PUBLICAR` | 5 |
| GET | `/api/v1/archive/event/{eventId}` | | ✔ | `PUBLICAR` | 5 |
| GET | `/api/v1/archive/public` | | ✔ | `PUBLICAR` (paginado; share link) | 5 |
| GET | `/api/v1/archive/my` | | ✔ | `PUBLICAR` (paginado) | 5 |
| PUT | `/api/v1/archive/{archiveId}` | | ✔ | `PUBLICAR` | 5 |
| POST | `/api/v1/archive/{archiveId}/media` | | ✔ | `PUBLICAR` | 5 |
| GET | `/api/v1/archive/{archiveId}/media` | | ✔ | `PUBLICAR` (paginado) | 5 |
| GET | `/api/v1/archive/media/{mediaId}` | | ✔ | `PUBLICAR` | 5 |
| POST | `/api/v1/archive/{archiveId}/comments` | | ✔ | `DIFERIR` | — |
| GET | `/api/v1/archive/{archiveId}/comments` | | ✔ | `DIFERIR` | — |
| PUT | `/api/v1/archive/comments/{commentId}` | | ✔ | `DIFERIR` | — |
| DELETE | `/api/v1/archive/comments/{commentId}` | | ✔ | `DIFERIR` | — |
| POST | `/api/v1/archive/media/{mediaId}/like` | | ✔ | `DIFERIR` | — |
| DELETE | `/api/v1/archive/media/{mediaId}/like` | | ✔ | `DIFERIR` | — |

## Chat — `chat-service` (:8088) — fuera de los 10 hitos del MVP

| Método | Ruta | Contrato | Front | Decisión propuesta | Ola |
|--------|------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/chat/rooms` | ✔ | ✔ | `CONTRATO` | 0 |
| POST | `/api/v1/chat/rooms/{roomId}/join` | ✔ | ✔ | `CONTRATO` | 0 |
| POST | `/api/v1/chat/messages` | ✔ | ✔ | `CONTRATO` | 0 |
| GET | `/api/v1/chat/rooms/{roomId}/messages` | ✔ | ✔ | `CONTRATO` | 0 |
| GET | `/api/v1/chat/rooms/{roomId}` | | ✔ | `PUBLICAR` | 6 |
| GET | `/api/v1/chat/rooms/event/{eventId}` | | ✔ | `PUBLICAR` | 6 |
| GET | `/api/v1/chat/rooms/user` | | ✔ | `PUBLICAR` | 6 |
| POST | `/api/v1/chat/rooms/{roomId}/leave` | | ✔ | `PUBLICAR` | 6 |
| POST | `/api/v1/chat/rooms/{roomId}/read` | | ✔ | `PUBLICAR` | 6 |
| PUT | `/api/v1/chat/messages/{messageId}` | | | `DIFERIR` | — |
| DELETE | `/api/v1/chat/messages/{messageId}` | | | `DIFERIR` | — |

## Servicios Python (FastAPI)

| Método | Ruta | Servicio | Contrato | Front | Decisión propuesta | Ola |
|--------|------|----------|:-------:|:-----:|--------------------|:---:|
| POST | `/api/v1/recommendations/events` | recommendation | ✔ | | `CONTRATO` (revisar si es MVP) | 6 |
| POST | `/api/v1/recommendations/users` | recommendation | | | `DIFERIR` | — |
| POST | `/api/v1/recommendations/content` | recommendation | | | `DIFERIR` | — |
| GET | `/api/v1/recommendations/trending` | recommendation | | | `DIFERIR` | — |
| GET | `/api/v1/recommendations/similar/{event_id}` | recommendation | | | `DIFERIR` | — |
| POST | `/api/v1/moderation/check` | moderation | ✔ | | `INTERNO` (pipeline de subida; retirar del contrato público vía RFC) | 6 |
| POST | `/api/v1/moderation/report` | moderation | | | `DIFERIR` | — |
| GET | `/api/v1/moderation/stats/{event_id}` | moderation | | | `DIFERIR` | — |
| GET | `/api/v1/moderation/reports` | moderation | | | `DIFERIR` | — |
| PUT | `/api/v1/moderation/reports/{report_id}/resolve` | moderation | | | `DIFERIR` | — |
| POST | `/api/v1/transcode` | transcode | | | `INTERNO` (Kafka) — retirar ruta del gateway | — |
| GET | `/api/v1/transcode/{job_id}/status` | transcode | | | `INTERNO` | — |
| POST | `/api/v1/thumbnail` | transcode | | | `INTERNO` | — |

## WebSockets (van a `asyncapi.yaml`)

| Ruta | Servicio | Estado en código | Estado en front | Ola |
|------|----------|------------------|-----------------|:---:|
| `/ws/checkin/{eventId}` | checkin | WS crudo registrado ✔ | conecta sin `?token=` | 2 |
| `/ws/mosaic/{eventId}` | mosaic | handler **sin** `WebSocketConfig` | conecta sin `?token=` | 3 |
| `/ws/streaming/{streamId}` | streaming | handler **sin** `WebSocketConfig` | conecta sin `?token=` | 4 |
| `/ws/chat` | chat | **STOMP/SockJS** (viola ADR-006) | WS crudo con `?userId=` (ADR-015 lo retira; T-F04) | 6 |

Ninguna ruta WS pasa por el gateway: falta la ruta `/ws/**` (O-08, T-B04).

## Totales por decisión propuesta

| Decisión | Filas |
|----------|------:|
| `CONTRATO` (ya publicado; incluye `GET /events` sin implementar) | 14 |
| `PUBLICAR` (incluye `invitations/respond`, pendiente de D-01) | 53 |
| `FUSIONAR` | 10 |
| `INTERNO` (incluye `moderation/check`, hoy en el contrato) | 7 |
| `DIFERIR` | 18 |
| **Total** | **101** = 100 del código + `GET /events` |
