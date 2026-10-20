# ADR-014: Listado de eventos con `GET /events?scope=`

- **Estado:** Aceptada
- **Fecha:** 2026-10-19
- **Decisor:** Ambos leads (se formaliza con la aprobación del PR)
- **Resuelve:** D-03 de `docs/roadmap/decisions-pending.md` y la observación O-26
- **Relacionado:** ADR-001 (estructura del evento), ADR-002 (array plano), ADR-003 (errores),
  ADR-007 (`X-User-*`), ADR-008 (paginación)

## Contexto

El contrato publica `GET /api/v1/events` (`listEvents`), que el frontend usa como
"mis eventos" (`event_remote_datasource.dart`). El backend no lo implementa: expone
`GET /events/my-events` (eventos que organiza el usuario) y `GET /events/upcoming`
(eventos publicados y futuros), ninguno de los dos en el contrato. Además, el frontend
llama a `/events/upcoming` fuera del contrato.

Al revisar el código para esta decisión aparecieron tres defectos que hay que corregir
con cualquiera de las dos opciones:

- `findUpcomingEvents` filtra por `status = PUBLISHED` y por fecha, pero **no por
  `settings.isPublic`**: cualquier usuario autenticado lista eventos privados ajenos
  (título, fechas, descripción). Viola el Principio III.
- Los listados no limitan `size` a 100 (Principio VII, ADR-008).
- Los índices de `events` en `01-init.js` usan campos anteriores a ADR-001
  (`schedule.start`, `location.coordinates`), así que ninguna consulta de listado los usa.

## Decisión

Se mantiene **una sola operación**, `listEvents`, y se le agrega un filtro `scope`:

```
GET /api/v1/events?scope=mine|upcoming&page=0&size=20
```

| `scope` | Contenido | Orden |
|---------|-----------|-------|
| `mine` (por defecto) | Eventos cuyo `organizerId` es el usuario autenticado, en cualquier estado. | `createdAt` descendente (CONVENTIONS §5). |
| `upcoming` | Eventos con `status = PUBLISHED`, `settings.isPublic = true` y `details.startDate` ≥ ahora. | `details.startDate` ascendente: excepción a CONVENTIONS §5, que el contrato documenta. |

Reglas:

- **Identidad:** el usuario sale solo de `X-User-Id` (ADR-007). El contrato no acepta
  `organizerId`, `userId` ni ningún otro parámetro de identidad.
- **Valor por defecto seguro:** sin `scope`, la respuesta es `mine`; nunca un listado global.
- **Validación:** `scope` es un enum cerrado. Un valor desconocido o `size` > 100 responde
  `400 VALIDATION_ERROR` (ADR-003, ADR-009).
- **Autenticación:** obligatoria en ambos scopes (`401` sin token).
- **Visibilidad:** todo evento listado pasa por la misma regla de `GET /events/{eventId}`:
  `hideAddressUntilAccepted` oculta dirección y coordenadas a quien no es organizador ni tiene RSVP `ACCEPTED`.
- **Respuesta:** array plano de `Event` (ADR-002) con `page`/`size` (ADR-008).
- **Índices:** `{organizerId: 1, createdAt: -1}` y
  `{status: 1, "settings.isPublic": 1, "details.startDate": 1}`, declarados en
  `01-init.js` (Principio VII, SLO de lectura < 300 ms).
- Se retiran `GET /events/my-events` y `GET /events/upcoming` del backend cuando el
  frontend use `?scope=`. No entran al contrato.

## Consecuencias

- El cambio de contrato es aditivo: `listEvents` gana el parámetro `scope`, y el cliente
  actual (que no lo envía) recibe `mine`, que es lo que espera.
- Un listado nuevo (p. ej. `attending`: eventos con invitación o RSVP del usuario) se agrega
  como valor del enum mediante RFC, sin rutas nuevas.
- La fuga de eventos privados en `/events/upcoming` se corrige ya, sin esperar al contrato
  (T-B17), porque esa ruta está expuesta hoy por el gateway.
- Tareas: T-C06 (contrato + fixtures), T-B07 (backend), T-F08 (frontend) y T-B17 (fuga).

## Alternativas consideradas

| Alternativa | Por qué no |
|-------------|------------|
| B: publicar `GET /events/my-events` y `GET /events/upcoming` y retirar `listEvents` | Rompe el contrato y el cliente actual; cada listado nuevo sería una ruta más; las rutas literales comparten espacio con `/events/{eventId}` y confunden el mock y las herramientas de OpenAPI. Su ventaja (poder abrir `upcoming` sin login con una regla del gateway) no está en el roadmap. |
| `scope=mine\|upcoming\|public` (propuesta original de D-03) | `public` se solapa con `upcoming`. Se agrega si aparece un caso que `upcoming` no cubra. |
| Parámetros de filtro libres (`?organizerId=`, `?isPublic=`) | Permite pedir datos de otro usuario y obliga a validar combinaciones; el `scope` cerrado es más simple y seguro. |
