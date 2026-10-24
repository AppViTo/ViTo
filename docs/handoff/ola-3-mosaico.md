# Handoff: Ola 3 — Mosaico en Vivo (T-C07 → T-F05, T-F10, T-B26)

Contrato: `docs/api/openapi.yaml` (tag `Mosaic`, #182) y `docs/api/asyncapi.yaml`
(`/ws/mosaic/{eventId}`, #171 y #182). Fixtures: `docs/api/fixtures/mosaic/`.
Reglas que aplican: ADR-016 (dueños), excepción A3 de T051 (solo imágenes, post-moderación),
ADR-002/008 (array plano y paginación), ADR-003 (errores), ADR-007 (identidad por `X-User-Id`).

## Contrato REST

| operationId | Ruta | Quién | Respuesta |
|-------------|------|-------|-----------|
| `createMosaicPost` | `POST /api/v1/mosaic/post` | organizador o RSVP `ACCEPTED` | `201` `MosaicFeedItem` |
| `listMosaicFeed` | `GET /api/v1/mosaic/feed/{eventId}?page&size` | organizador, invitados; cualquiera si el evento es público | `200` array plano |
| `getMosaicGrid` | `GET /api/v1/mosaic/grid/{eventId}` | igual que el feed | `200` `MosaicGrid` |
| `initializeMosaicGrid` | `POST /api/v1/mosaic/grid/{eventId}/initialize?rows&cols` | organizador | `201` `MosaicGrid`; `409` si ya existe |
| `getMosaicItem` | `GET /api/v1/mosaic/item/{itemId}` | igual que el feed | `200` `MosaicFeedItem` |
| `likeMosaicItem` | `POST /api/v1/mosaic/item/{itemId}/like` | igual que el feed | `200` `MosaicLikeState` (idempotente) |
| `unlikeMosaicItem` | `DELETE /api/v1/mosaic/item/{itemId}/like` | igual que el feed | `200` `MosaicLikeState` (idempotente) |
| `removeMosaicItem` | `DELETE /api/v1/mosaic/item/{itemId}` | organizador | `204` |

- **Subida en dos pasos (ADR-016):** `uploadMedia` (`multipart`, `file` + `eventId`) devuelve
  `Media.id`; luego `createMosaicPost` con `{ eventId, mediaId, content? }`. El `Media` debe
  ser imagen (`image/jpeg`, `image/png`, `image/webp`), del mismo evento y del mismo usuario.
- **Sin video (A3):** `uploadMedia` y `createMosaicPost` rechazan `video/mp4` con `400`.
- **Post-moderación (A3):** la foto se publica al instante. `removeMosaicItem` es un borrado
  lógico: el ítem sale de feed, grilla, `getMosaicItem` y likes (`404`), y su celda vuelve a
  `null`.
- **Retiradas:** `/mosaic/photo` (la reemplazan los dos pasos) y `/mosaic/checkin` (interno:
  `mosaic-service` lo crea al consumir `checkin-events`). Ninguna está en el contrato.
- **Acceso sin permiso:** feed y grilla responden `403`; ítem y likes responden `404`, para no
  revelar que el ítem existe.

## Contrato WebSocket (`/ws/mosaic/{eventId}?token=`)

| `type` | Cuándo | Payload |
|--------|--------|---------|
| `NEW_ITEM` | tras `createMosaicPost` o un check-in | `item: MosaicFeedItem` |
| `GRID_UPDATE` | cambia una celda | `eventId`, `grid` (igual que `MosaicGrid.grid`) |
| `LIKE_UPDATE` | like o unlike | `itemId`, `likesCount` |
| `ITEM_REMOVED` | tras `removeMosaicItem` | `eventId`, `itemId` |

## Para backend (T-B26 y alineación de `mosaic-service`)

Hoy el código no cumple el contrato (ADR-010: gana el contrato):
- `AddPostCommand` no tiene `mediaId`; hay que validarlo contra `media-service`.
- Like y unlike devuelven cuerpo vacío; deben devolver `MosaicLikeState`.
- `initializeGrid` no responde `409` y `MosaicGrid.addItem` supone 4 columnas.
- Ninguna ruta revisa la autorización de la tabla de arriba.
- `removeMosaicItem` y `ITEM_REMOVED` no existen (T-B26).

## Para frontend (T-F05, T-F10)

- `addPost` envía `mediaId`; `addPhoto` (`/mosaic/photo`) y `addCheckIn` (`/mosaic/checkin`)
  se dejan de llamar (T-F05). El selector solo ofrece imágenes.
- `likePost` y `unlikePost` pueden usar el `MosaicLikeState` de la respuesta.
- El bloc maneja `ITEM_REMOVED`: quita el ítem del feed y vacía su celda (T-F10).
- La acción "retirar" solo se muestra al organizador del evento (T-F10).
- Tests de serialización contra `fixtures/mosaic/` con `test/contract/fixture_loader.dart`.

## Criterios de aceptación

- `npm run contract` y `npm run lint:async` sin errores.
- Envelope de error de ADR-003 en todo 4xx; listas como array plano.
- Identidad solo por `X-User-Id` (el cliente nunca envía `userId`).
