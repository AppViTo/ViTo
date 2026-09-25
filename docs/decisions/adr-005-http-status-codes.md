# ADR-005: Códigos HTTP de creación, borrado y contenido

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), aprobada por ambos leads

## Contexto

Deriva actual entre contrato y código:

| Operación | `openapi.yaml` | Backend real |
|-----------|----------------|--------------|
| `POST /api/v1/events` | `201 Created` | `200 OK` (`EventController.java` L33) |
| `POST /api/v1/auth/register` | `201` | `201` ✓ |
| `DELETE /api/v1/events/{id}` | no documentado | `204` |

El frontend ignora el código HTTP en casi todos los casos (lee `response.data`), así que la deriva no rompe pantallas — pero sí rompe la validación por contrato (`schemathesis`) y la credibilidad del documento.

## Decisión

| Situación | Código | Cuerpo |
|-----------|--------|--------|
| `POST` que crea un recurso con URL propia | `201 Created` | el recurso creado |
| `POST` que ejecuta una acción sin crear recurso | `200 OK` | resultado de la acción |
| `GET`, `PUT`, `PATCH` | `200 OK` | recurso / resultado |
| `DELETE` sin cuerpo | `204 No Content` | vacío |
| Operación idempotente repetida | `200` (no `409`) | el recurso actual |

Aplicación concreta:

- `POST /events` → **201** con el `Event`.
- `POST /auth/register` → **201** con `AuthResponse`.
- `POST /events/{id}/rsvp`, `/invitations`, `/checkin`, `/mosaic/post`, `/streaming` → **200** (accionan sobre recursos existentes o devuelven entidades ya existentes en el sistema).
- `DELETE /events/{id}`, `/checkin/ticket/{id}`, `/social/follow/{id}` → **204**.

El `openapi.yaml` actualizado documenta el código correcto para cada operación y `schemathesis` lo verifica en CI del backend.

## Consecuencias

- `event-service` cambia `@ResponseStatus(HttpStatus.OK)` → `CREATED` en `POST /` (una línea).
- Aparece una regla verificable por linter de contrato en vez de una discusión recurrente.

## Alternativas consideradas

- **`200` en todo:** menos estricto, pero rompe la semántica REST y hace que el contrato no sea generable a clientes.
- **Dejar la deriva y documentar "acepta 200 o 201":** los clientes no pueden tratarlo de forma determinista.
