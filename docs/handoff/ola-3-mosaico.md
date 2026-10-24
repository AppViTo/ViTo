# Handoff: Ola 3 — Mosaico en Vivo & Contrato (T-C07, T-F05)

> **Estado (2026-10-24):** #171 incorporó solo el punto 2 (`asyncapi.yaml`). Los puntos 1 y 3
> (rutas REST en `openapi.yaml` y fixtures en `docs/api/fixtures/mosaic/`) y el endpoint de
> moderación del organizador (excepción A3, T-B26) siguen pendientes en T-C07.

## Alcance
1. **Contrato REST (`docs/api/openapi.yaml`)**:
   - `POST /api/v1/mosaic/post`: crea una entrada en el mosaico vinculada a un `mediaId` (ADR-016).
   - `GET /api/v1/mosaic/feed/{eventId}`: feed cronológico paginado (`page`, `size`).
   - `GET /api/v1/mosaic/grid/{eventId}`: matriz actual del mosaico.
   - `POST /api/v1/mosaic/grid/{eventId}/initialize`: inicializa la retícula (solo organizador).
   - `GET /api/v1/mosaic/item/{itemId}`: detalle de un elemento.
   - `POST /api/v1/mosaic/item/{itemId}/like`: dar like.
   - `DELETE /api/v1/mosaic/item/{itemId}/like`: retirar like.
   - Retiro de `/mosaic/photo` (reemplazado por `/media/upload` + `/mosaic/post`) y pase de `/mosaic/checkin` a interno.

2. **Contrato WebSocket (`docs/api/asyncapi.yaml`)**:
   - `/ws/mosaic/{eventId}` con eventos `NEW_ITEM`, `GRID_UPDATE`, `LIKE_UPDATE`.

3. **Fixtures y validación**:
   - Añadidos todos los fixtures en `docs/api/fixtures/mosaic/`.
   - `npm run contract` pasa limpio.

## Criterios de Aceptación
- Endpoints operando bajo envelope estándar y arrays planos según CONVENTIONS.md.
- Autenticación Bearer + header `X-User-Id`.
