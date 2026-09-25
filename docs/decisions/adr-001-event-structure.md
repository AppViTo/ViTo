# ADR-001: Estructura del recurso Event

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), aprobada por ambos leads

## Contexto

El contrato actual (`docs/api/openapi.yaml`, schema `Event`) y parte del backend existente (`event-service`) modelan el evento con campos planos en la raíz:

```json
{ "name": "...", "date": "...", "endDate": "...", "isPublic": true,
  "maxAttendees": 100, "currentAttendees": 0, "category": "...",
  "status": "ACTIVE", "location": { } }
```

Los modelos Dart ya construidos (`frontend/vito_app/lib/features/events/data/models/event_model.dart`) esperan una estructura anidada distinta, con `title`, `details{startDate,endDate,location,address,latitude,longitude,coverImages,tags,category}`, `settings{isPublic,requiresApproval,maxAttendees,allowGuestInvites,enableLiveStreaming,enableMosaic}` y `stats{...}`.

El backend existente (`EventController` + `EventModel`) **ya usa la forma anidada**; quien diverge es el `openapi.yaml` viejo.

## Decisión

El contrato adopta la estructura anidada que espera el frontend. Ver `docs/api/CONVENTIONS.md` §6 para el JSON completo.

| Campo | Definición |
|-------|------------|
| `title` | nombre del evento (antes `name`) |
| `details` | fecha, lugar, geolocalización, imágenes, tags, `category` |
| `settings` | flags de visibilidad y capacidad (`isPublic`, `maxAttendees`,…) |
| `stats` | contadores derivados, **solo lectura**, calculados por el backend |
| `status` | `DRAFT` \| `PUBLISHED` \| `CANCELLED` \| `COMPLETED` |

**No existen** `name`, `date`, `endDate`, `isPublic`, `maxAttendees`, `currentAttendees`, `category` en la raíz del objeto.

## Consecuencias

- El backend debe mapear sus modelos internos a esta forma (o ajustarlos; ya coinciden en `event-service`).
- `POST /events` recibe `title`, `details`, `settings` como body.
- Los enums de `status` dejan de incluir `ACTIVE`.
- El frontend no cambia código de modelos.

## Alternativas consideradas

- **Modelo plano estilo REST clásico:** más legible en curl, pero obliga a reescribir 4 modelos Dart y los 3 puntos que los usan (listado, detalle, creación) sin ganancia funcional.
- **Adaptar el frontend al backend:** descartada por decisión del equipo frontend de mantener sus modelos ya construidos como fuente de la forma del contrato.
