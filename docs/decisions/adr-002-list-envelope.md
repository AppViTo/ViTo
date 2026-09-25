# ADR-002: Las listas se devuelven como array plano

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), aprobada por ambos leads

## Contexto

El contrato vigente usa dos envelops distintos y contradictorios:

- `GET /api/v1/events` → `{ "events": [ ... ], "total": N }`
- `GET /api/v1/chat/rooms/{roomId}/messages` → `{ "messages": [ ... ], "total": N }`

Los datasources Dart hacen `(response.data as List)` en todas las listas (`event_remote_datasource.dart`, `chat_remote_datasource.dart`, `social_remote_datasource.dart`, `checkin_remote_datasource.dart`, `mosaic_remote_datasource.dart`, `streaming_remote_datasource.dart`, `archive_remote_datasource.dart`). Con el envelope `Map`, esos parsers lanzan `type 'Map' is not a subtype of type 'List'`.

Los controllers backend ya devuelven `List<T>` directamente.

## Decisión

**Todas** las respuestas de lista son un **array JSON plano**, sin envelope, sin `total`, sin clave que las envuelva.

```json
[ { "id": "evt_1" }, { "id": "evt_2" } ]
```

Una sola excepción requiere RFC expreso y ADR nuevo.

## Consecuencias

- El contrato (OpenAPI) tipa las respuestas de lista como `type: array, items: {$ref: ...}`.
- **No hay `total` disponible.** Si una pantalla necesita "página X de Y", ese total se expone como campo propio explícito del endpoint (p.ej. `X-Total-Count` header o propiedad nombrada), nunca como envelope genérico.
- El backend simplifica controladores: retornan el `List` del servicio.
- Se elimina la divergencia entre `events` y `messages`.

## Alternativas consideradas

- **Envelope `{items, page, size, total}` (estilo Spring Data):** más completo y estándar, pero obliga a tocar los 7 datasources Dart y sus repositorios, y a reescribir todos los parsers de paginación. Costo alto, beneficio bajo para el MVP.
- **Envelope por recurso (`{events:[]}`, `{messages:[]}`):** descartado por ser inconsistente entre endpoints (exactamente el problema que se está resolviendo).
