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

`details.location` es un **string libre** ("Hacienda El Retiro"), no un objeto
`{name, address, city, lat, lng}`. El `EventLocation` plano del YAML viejo se elimina.

## Consecuencias

- El backend debe mapear sus modelos internos a esta forma (o ajustarlos; ya coinciden en `event-service`).
- `POST /events` recibe `title`, `details`, `settings` como body.
- Los enums de `status` dejan de incluir `ACTIVE`.
- El frontend no cambia código de modelos.

## Actualizaciones

### 2026-10-02 — `settings.hideAddressUntilAccepted`

El wizard de creación añadió el switch *"Mostrar solo el barrio hasta que acepten"*.
`settings` incorpora el campo **`hideAddressUntilAccepted: boolean`**, default `false`.

Comportamiento: cuando es `true`, un invitado cuyo RSVP no está en `ACCEPTED` solo ve
el barrio, no la dirección exacta; el organizador siempre ve la dirección completa.
Debe aplicarse al servir `GET /events/{eventId}`, no en el guardado.

Alineación del contrato en la misma fecha:

- `docs/api/openapi.yaml` — schemas `CreateEventRequest` y `Event` reescritos a esta
  forma anidada; `EventLocation` eliminado; `GET /events` pasada a array plano (ADR-002);
  `ErrorResponse` completado con `status` (ADR-003); `operationId`, `description` y
  envelope de error en todas las operaciones. `npx @stoplight/spectral-cli lint`
  pasa de **40 errores + 1 warning a 0**.
- `docs/api/CONVENTIONS.md` §6 — ejemplo del evento actualizado con el campo nuevo.
- `.spectral.yaml` — la regla `vito-public-auth-paths` usaba `function: falsy`, que en
  Spectral no acepta arrays vacíos: `security: []` (la forma OpenAPI válida de marcar un
  endpoint público) nunca podía pasar. Sustituida por `schema` con `maxItems: 0`.

## Alternativas consideradas

- **Modelo plano estilo REST clásico:** más legible en curl, pero obliga a reescribir 4 modelos Dart y los 3 puntos que los usan (listado, detalle, creación) sin ganancia funcional.
- **Adaptar el frontend al backend:** descartada por decisión del equipo frontend de mantener sus modelos ya construidos como fuente de la forma del contrato.
