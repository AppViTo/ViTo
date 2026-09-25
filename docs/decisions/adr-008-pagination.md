# ADR-008: Paginación `page`/`size` sin `total`

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), aprobada por ambos leads

## Contexto

- `docs/architecture.md` afirma: *"All list endpoints support pagination"*.
- En la práctica la mayoría de endpoints aceptan `page`/`size` pero **devuelven la lista completa sin envoltorio** (por ADR-002), así que el cliente no puede saber cuántas páginas hay.
- Solo 2 de 15 operaciones del OpenAPI actual declaran `page`/`size`.
- El frontend ya envía `?page=&size=` en casi todos los datasources.

## Decisión

1. **Todas** las respuestas de lista aceptan `page` (base 0, default `0`) y `size` (default `20`, máximo `100`). Un endpoint de lista sin paginación es un error de contrato salvo ADR expreso.
2. La respuesta sigue siendo un **array plano** (ADR-002) ⇒ **no hay `total`**.
3. El "cargar más" del cliente se resuelve con la heurística: `si la página devuelta tiene menos de `size` elementos, no hay más`.
4. Orden por defecto `createdAt` descendente.

### Excepción: cuando el total es imprescindible

Si una pantalla necesita mostrar un conteo total (p.ej. "128 asistentes"), el endpoint expone un campo **nombrado y específico**, no un envelope genérico:

```json
// GET /api/v1/checkin/event/{eventId}/stats
{ "eventId": "...", "totalExpected": 120, "totalCheckedIn": 80, "checkInRate": 0.66 }
```

Eso ya existe en `stats`, `CheckInStatsModel` y `LiveStream.stats`. **No** se introduce `{items, total}`.

## Consecuencias

- `openapi.yaml` define `components.parameters.Page` y `components.parameters.Size` reutilizables; hoy solo 2 operaciones las tienen.
- El backend confirma que sus repositorios aplican `skip/limit` de verdad (hasta ahora varios devuelven la lista completa ignorando los parámetros — **bug a verificar en cada handoff**).
- El frontend no necesita `total` para su patrón de scroll infinito.

## Alternativas consideradas

- **Envelope `{items, page, size, total}` (Spring Data):** el estándar de facto, pero choca con ADR-002 y obliga a reescribir los 7 datasources Dart.
- **Cursor/offset `?cursor=`:** mejor para feeds de alta escritura (mosaico, chat), peor para pantallas de administración. Se posterga; si se necesita, será un RFC nuevo y solo para los endpoints de feed.
