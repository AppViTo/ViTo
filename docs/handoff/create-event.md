# HANDOFF — `create-event`

> **Para quién es:** el agente/desarrollador del equipo backend que implementa esta feature.
> **Regla:** este documento describe el *qué*. Tú decides el *cómo*. Si necesitas cambiar algo de aquí, abre un RFC (issue con template `contract-change`) — **no diverjas del contrato en tu código**.

| | |
|---|---|
| **Equipo responsable** | Backend: `event-service` (creación) · `media-service` (portada) · Frontend: `CreateEventScreen` (`frontend/vito_app/lib/features/events/presentation/pages/create_event_screen.dart`) |
| **Contrato** | [`docs/api/openapi.yaml`](../api/openapi.yaml) (tag `Events`, tag `Media`) · `asyncapi.yaml` **no existe todavía** |
| **Fixtures** | `docs/api/fixtures/create-event/` — 🔴 **no existe** (ver §6) |
| **Convenciones generales** | [`docs/api/CONVENTIONS.md`](../api/CONVENTIONS.md) — leer antes de empezar |
| **Estado** | 🔴 No iniciado |

---

> ## ✅ Contrato alineado (2026-10-02)
>
> `openapi.yaml` y ADR-001 ya **conversan sobre la misma versión de la app**:
> los schemas `CreateEventRequest`/`Event` se reescribieron a la forma anidada
> (`title` + `details` + `settings` + `stats`), se eliminó `EventLocation` plano,
> `GET /events` pasó a array plano (ADR-002) y `ErrorResponse` se completó con `status`
> (ADR-003). `settings` incorpora `hideAddressUntilAccepted` en ADR-001, CONVENTIONS §6
> y el YAML.
>
> Verificación: `npx @stoplight/spectral-cli lint docs/api/openapi.yaml` → **0 problemas**
> (antes: 40 errores + 1 warning). El detalle está en la sección *Actualizaciones* de
> [`docs/decisions/adr-001-event-structure.md`](../decisions/adr-001-event-structure.md).

---

## 1. Contexto

`CreateEventScreen` es el wizard de 3 pasos por el que un usuario crea un evento en ViTo:
identidad (nombre, tipo, mensaje, portada) → cuándo y dónde (rango de fechas, dirección,
privacidad) → a quién invitar (buscador de usuarios, enlace de invitación, acompañantes).

Es la puerta de entrada de todo el ciclo de vida de la plataforma: sin evento no hay
RSVP, QR, check-in, mosaico ni streaming. El MVP mide su éxito con **RSVP completado > 80 %**,
que depende de que la invitación llegue bien.

## 2. Endpoints

### `POST /api/v1/events` — crear evento

- **Auth:** `bearerAuth` + `X-User-Id` inyectado por el gateway (ADR-007). `organizerId` = `X-User-Id`.
- **Rate limit:** 20 req/s, burst 40 (CONVENTIONS §9).
- **HTTP:** `201 Created` con el `Event` creado (ADR-005).
- **Request** — exactamente lo que envía `EventRemoteDataSourceImpl.createEvent`:

  ```json
  {
    "title": "Los 30 de Marta",
    "description": "Te espero con toda el alma",
    "details": {
      "startDate": "2026-08-23T01:00:00.000Z",
      "endDate": "2026-08-23T07:00:00.000Z",
      "location": null,
      "address": "Cra. 7 #63-14, Chapinero Alto",
      "latitude": null,
      "longitude": null,
      "coverImages": null,
      "tags": null,
      "category": "BIRTHDAY"
    },
    "settings": {
      "isPublic": false,
      "requiresApproval": false,
      "maxAttendees": 100,
      "allowGuestInvites": false,
      "enableLiveStreaming": true,
      "enableMosaic": true,
      "hideAddressUntilAccepted": true
    }
  }
  ```

- **Response 201** — parseada por `EventModel.fromJson`:

  ```json
  {
    "id": "evt_01H8X...",
    "organizerId": "usr_01H8X...",
    "title": "Los 30 de Marta",
    "description": "Te espero con toda el alma",
    "details": { "…": "igual que el request" },
    "settings": { "…": "igual que el request" },
    "stats": {
      "totalInvited": 0, "totalAccepted": 0, "totalDeclined": 0,
      "totalMaybe": 0, "totalWaitlisted": 0, "totalCheckedIn": 0,
      "totalViewers": 0
    },
    "status": "PUBLISHED",
    "createdAt": "2026-10-02T14:03:22Z",
    "updatedAt": "2026-10-02T14:03:22Z"
  }
  ```

- **Errores:**

  | Código | `error` | Cuándo |
  |--------|---------|--------|
  | 400 | `VALIDATION_ERROR` | `title` ausente, < 3 o > 100 caracteres; `description` > 2000; `endDate` ≤ `startDate`; `category` fuera del enum; `hideAddressUntilAccepted` no es boolean |
  | 401 | `UNAUTHORIZED` | sin `Authorization` o JWT inválido |
  | 429 | `RATE_LIMITED` | > 20 req/s, con header `Retry-After` |

### `GET /api/v1/events` — listado del usuario

- **Auth:** `bearerAuth`. **Rate limit:** 20 req/s.
- **Query:** `?page=0&size=20` (base 0, default 20, máx 100 — ADR-008).
- **Response 200:** el frontend hace `(response.data as List)` → **array plano** (ADR-002).

  ```json
  [ { "id": "evt_1", "title": "Los 30 de Marta", "status": "PUBLISHED" } ]
  ```

  > ⚠️ El YAML tipa `{ "events": [...], "total": N }`. **Contradice ADR-002 y ADR-008** y haría
  > fallar el parser Dart (`type 'Map' is not a subtype of type 'List'`). Incluir en el mismo RFC.

- **Errores:** 401 `UNAUTHORIZED`.

### `GET /api/v1/events/{eventId}` — detalle

- **Auth:** `bearerAuth`.
- **Response 200:** `Event` plano (mismo shape que la respuesta de `POST`).
- **Errores:**

  | Código | `error` | Cuándo |
  |--------|---------|--------|
  | 401 | `UNAUTHORIZED` | sin token |
  | 404 | `NOT_FOUND` | evento inexistente |

### `POST /api/v1/media/upload` — portada del evento

- **Auth:** `bearerAuth` + `X-User-Id` (CONVENTIONS §2.2: `media-service` devuelve 400 si el header va vacío).
- **Content-Type:** `multipart/form-data`.
- **Request:**

  | Campo | Tipo | Obligatorio | Notas |
  |-------|------|-------------|-------|
  | `file` | binario | sí | `image/jpeg`, `image/png`, `image/webp` · máx **10 MB** |
  | `eventId` | string | sí | el evento debe existir y el usuario debe ser su organizador |

- **Response 201:**

  ```json
  {
    "id": "med_01H8X...",
    "eventId": "evt_01H8X...",
    "uploadedBy": "usr_01H8X...",
    "fileName": "portada.jpg",
    "contentType": "image/jpeg",
    "fileSize": 482113,
    "status": "UPLOADED",
    "location": {
      "bucket": "vito-events",
      "key": "evt_01H8X/portada.jpg",
      "url": "https://cdn.vito.com/evt_01H8X/portada.jpg",
      "thumbnailUrl": "https://cdn.vito.com/evt_01H8X/thumb_portada.jpg"
    },
    "uploadedAt": "2026-10-02T14:03:22Z"
  }
  ```

  El frontend lee **`location.url`**; si no viene, devuelve cadena vacía y no falla.

- **Errores:**

  | Código | `error` | Cuándo |
  |--------|---------|--------|
  | 400 | `VALIDATION_ERROR` | falta `eventId`; tipo no permitido; > 10 MB |
  | 401 | `UNAUTHORIZED` | sin token |
  | 404 | `NOT_FOUND` | `eventId` inexistente |
  | 403 | `FORBIDDEN` | el usuario no es organizador del evento |

> **Orden obligatorio:** el cliente **crea el evento primero y sube la portada después**, porque
> `/media/upload` exige `eventId`. Ver §4.6.

## 3. Esquemas

- `CreateEventRequest`, `Event`, `EventDetails`, `EventSettings`, `EventStats`, `Media`.
- **Referencia vinculante:** CONVENTIONS §6 "Estructura del Evento (ADR-001)" y ADR-001.
- Los schemas del `openapi.yaml` están **alineados** con ADR-001 desde 2026-10-02:
  forma anidada, enum `status` corregido, `EventLocation` plano eliminado
  (`details.location` es `string`), `GET /events` en array plano, `ErrorResponse`
  completo y `operationId`/`description` en las 14 operaciones.
- Si este documento y el YAML discrepan, **gana el YAML**.

## 4. Reglas de negocio

1. **`title`**: 3–100 caracteres tras trim. El frontend ya lo valida, pero el backend **debe
   revalidar** y devolver `400 VALIDATION_ERROR` con `message` en es-ES apuntando al campo.
2. **`description`**: máximo 2000 caracteres. Es opcional (si el usuario lo deja vacío el
   frontend no lo envía).
3. **`details.address`**: máximo 300 caracteres. Opcional.
4. **`details.endDate` debe ser estrictamente posterior a `details.startDate`**. El frontend lo
   valida antes de avanzar de paso; el backend es la última línea de defensa.
5. **Fechas en ISO-8601 UTC con sufijo `Z`** (CONVENTIONS §6). El frontend envía
   `date.toUtc().toIso8601String()`, p. ej. `2026-08-23T01:00:00.000Z` para un evento del
   22 de agosto a las 8:00 p. m. (UTC-5). El backend debe guardar el instante, no la hora local.
6. **`details.category`** es un enum `SCREAMING_SNAKE_CASE`. El wizard solo puede producir:

   | Chip del wizard | Valor enviado |
   |---|---|
   | Cumpleaños | `BIRTHDAY` |
   | Reunión | `MEETING` |
   | Boda | `WEDDING` |
   | Grado | `GRADUATION` |
   | Viaje | `TRAVEL` |
   | Fiesta | `PARTY` |

   Un valor fuera de la lista ⇒ `400 VALIDATION_ERROR`. Añadir valores exige RFC.
7. **`settings.isPublic` se envía siempre `false`**: el wizard crea eventos privados por defecto.
   Si en el futuro aparece un switch de visibilidad, cambiará a `true`/`false` según la UI.
8. **`settings.hideAddressUntilAccepted`** (en el contrato desde 2026-10-02; default `false`,
   el wizard envía `true`):
   `true` = quien aún no ha aceptado la invitación solo ve el barrio, no la dirección exacta.
   El backend debe **aplicar el ocultamiento al servir el detalle
   del evento** a un invitado cuyo RSVP no esté en `ACCEPTED`; al organizador siempre la
   dirección completa.
9. **`settings.allowGuestInvites`** = "permitir que traigan acompañante". El frontend arranca en
   `false`. Debe condicionar `guestsCount` en el RSVP.
10. **`POST /events` no acepta archivos.** La portada se sube aparte (§2 `POST /media/upload`).
11. **`POST /media/upload` valida el límite de 10 MB y los tipos en el servidor**, no solo en el
    cliente. Los formatos permitidos coinciden con CONVENTIONS §8.
12. **Orden atómico no requerido:** si la subida de portada falla, el evento ya creado **debe
    seguir existiendo**. El frontend avisa "Evento creado. No se pudo subir la portada." y navega
    al detalle de todos modos.

## 5. WebSockets (si aplica)

No aplica para esta feature. El wizard es 100 % REST.

## 6. Dependencias

| Qué | Quién lo da | Estado |
|-----|-------------|--------|
| Alinear schemas `CreateEventRequest`/`Event` con ADR-001 | ambos leads (CODEOWNERS: `/docs/api/`) | 🟢 2026-10-02 |
| `GET /events` como array plano (ADR-002/008) | ambos leads | 🟢 2026-10-02 |
| `operationId` + `description` en las 14 operaciones | ambos leads | 🟢 2026-10-02 |
| `docs/api/fixtures/create-event/` | ambos leads | 🔴 |
| `docs/api/asyncapi.yaml` | — (no aplica a esta feature) | ⚪ |
| Headers `X-User-*` en el gateway | equipo backend (ADR-007) | 🟢 |
| Almacenamiento S3/MinIO + CDN para `location.url` | DevOps | 🟡 |
| `media-service` con `POST /media/upload` | equipo backend | 🔴 |

## 7. Criterios de aceptación

- [ ] `POST /api/v1/events` responde **201** y el body coincide con el shape de §2 (ADR-001)
- [ ] `GET /api/v1/events` devuelve **array plano** y aplica `page`/`size` reales (ADR-002, ADR-008)
- [ ] `GET /api/v1/events/{eventId}` devuelve 404 con envelope ADR-003 si no existe
- [ ] `POST /api/v1/media/upload` acepta multipart con `file` + `eventId` y devuelve `location.url`
- [ ] Todas las respuestas 4xx/5xx traen `{error, message, status, timestamp}` con `message` en es-ES (ADR-003)
- [ ] Enums exactos de CONVENTIONS §6 (`Event.status`, `Media.status`, `details.category`)
- [ ] Las fechas se persisten como UTC y se devuelven con sufijo `Z`
- [ ] Se revalidan en servidor: `title` 3–100, `description` ≤ 2000, `address` ≤ 300, `endDate > startDate`
- [ ] `hideAddressUntilAccepted` oculta la dirección a invitados no aceptados
- [x] `npx @stoplight/spectral-cli lint docs/api/openapi.yaml` → **0 problemas** (antes: 40 errores + 1 warning)
- [ ] `schemathesis run --checks all <url-del-servicio>` pasa
- [ ] `mvn clean test` verde con tests unitarios de §4
- [ ] Ningún endpoint nuevo fuera de `openapi.yaml`

## 8. Fuera de alcance

Explicitamente **no** entra en esta feature (todos requieren RFC previo):

| Qué | Por qué |
|-----|---------|
| Envío de invitaciones (`POST /events/{id}/invitations`, en lote) | no está en `openapi.yaml`. El wizard **selecciona** invitados pero **no los envía**; el CTA "Enviar N invitaciones" hoy solo crea el evento |
| Enlace de invitación (crear/copiar/caducidad de 7 días) | no está en `openapi.yaml`; `_inviteLink` queda en `null` y la UI muestra un estado controlado |
| `PUT /api/v1/events/{eventId}` | no está en `openapi.yaml`. Sin él la portada **se sube** (queda ligada al `eventId`) pero **no puede** reflejarse en `details.coverImages`. Llamado hoy por `EventBloc.onUpdateEvent` |
| `DELETE /api/v1/events/{eventId}` (cancelar) | no está en `openapi.yaml`. Declarado en `EventRemoteDataSourceImpl.cancelEvent` con `dio.delete` |
| `GET /events/upcoming` | lo llama el datasource hoy, pero no existe en el contrato |
| RSVP, check-in, QR, mosaico, streaming | features posteriores |
| `CreateEventUseCase`/`RespondRSVPUseCase` sin usar en el frontend | deuda interna del equipo frontend |

## 9. Preguntas abiertas

| # | Pregunta | Respuesta |
|---|----------|-----------|
| 1 | **¿`status` inicial de un evento recién creado: `DRAFT` o `PUBLISHED`?** El frontend mapea cualquier valor desconocido/ausente a `DRAFT`, pero el wizard muestra "Evento creado" y el usuario espera poder compartirlo enseguida. Recomendación del frontend: **`PUBLISHED`**. | |
| 2 | ¿`details.location` debe poblarse a partir de `address` (geocodificación) o lo deja el cliente? El wizard no envía `latitude`/`longitude`. | |
| 3 | ~~¿Se acepta `settings.hideAddressUntilAccepted` en la primera entrega?~~ **Resuelto 2026-10-02:** sí — ya está en ADR-001, CONVENTIONS §6 y el YAML. | ✅ Aceptado |
| 4 | ¿Qué código de error para "el archivo supera los 10 MB"? Recomendación: `400 VALIDATION_ERROR` (CONVENTIONS §4: validación ⇒ 400, nunca 409). | |
| 5 | ¿El CDN de `location.url` es público o requiere firma? El QR y la galería post-evento dependerán de esto. | |

---
*Última actualización: `2026-10-02` · Autor: @alexvilla01*
