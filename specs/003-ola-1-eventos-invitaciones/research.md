# Research: Ola 1 del contrato — eventos, invitaciones y RSVP

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Fecha**: 2026-11-01

Cada sección resuelve una incógnita del *Technical Context*. Formato: Decisión ·
Justificación · Alternativas. El código citado es el de `develop` al 2026-11-01; según ADR-010
es referencia, no fuente de verdad.

---

## R1 · Cupo atómico y lista de espera (FR-015, FR-016, Principio VII)

**Hallazgo:** `EventServiceImpl.respondToRSVP` cuenta los `ACCEPTED` con
`countByEventIdAndStatus` y luego guarda: es leer-comparar-escribir, prohibido por el
Principio VII. Dos respuestas simultáneas por el último lugar pueden confirmarse las dos.
`updateEventStats` también lee y reescribe el documento del evento completo, y
`respondToInvitation` confirma sin mirar el cupo.

**Decisión:** el contador `stats.totalAccepted` del documento del evento es la fuente del
cupo. Confirmar es un `findAndModify` condicional sobre el evento:
`{_id, status: PUBLISHED, $or: [{settings.maxAttendees: 0}, {$expr: {$lt: [$stats.totalAccepted, $settings.maxAttendees]}}]}`
con `$inc: {stats.totalAccepted: 1}`. Si no modifica nada, la persona queda `WAITLISTED` con
su `waitlistedAt`. Liberar un lugar (`ACCEPTED` → `DECLINED`/`MAYBE`) hace `$inc: -1` y
después promueve: `findAndModify` del RSVP `WAITLISTED` más antiguo del evento (orden por
`waitlistedAt`) seguido del mismo incremento condicional; si el incremento falla, el RSVP
vuelve a `WAITLISTED`. Los demás contadores (`totalDeclined`, `totalMaybe`,
`totalWaitlisted`, `totalInvited`) se actualizan con `$inc`, nunca reescribiendo el documento.
`maxAttendees: 0` significa sin límite (comportamiento actual).

Índices: único `{eventId, userId}` en `rsvps` y en `invitations`; `{eventId, status,
waitlistedAt}` en `rsvps` para la promoción.

**Justificación:** una sola operación atómica por documento basta en Mongo sin
transacciones; cumple SC-003 (50 simultáneas, cupo 20 → 20 y 30). El índice único hace
idempotente el upsert del RSVP (FR-019).

**Alternativas:**
- *Transacción multi-documento (RSVP + evento):* exige replica set; el Mongo de Fase
  Tracción es un nodo (ADR-011).
- *Script Lua en Redis como semáforo:* dos fuentes de verdad del cupo y reconciliación al
  reiniciar Redis.

## R2 · Semántica de `POST /events/{eventId}/rsvp` (FR-014, FR-017, FR-019, D-01)

**Decisión:** upsert idempotente de la respuesta de quien llama. Cuerpo
`{status: ACCEPTED | DECLINED | MAYBE, message?}`; `WAITLISTED` y `PENDING` no se aceptan
como entrada (400). Respuesta `200` con el `Rsvp` resultante, cuyo `status` puede ser
`WAITLISTED` (así se informa la lista de espera). Repetir la misma respuesta devuelve el
mismo resultado sin cambiar contadores. Se elimina el error actual `alreadyRSVP`: cambiar de
respuesta es un caso normal.

Si hay invitación, se actualiza en la misma operación: `ACCEPTED` o `WAITLISTED` →
invitación `ACCEPTED`; `DECLINED` → `DECLINED`; `MAYBE` → sigue `PENDING`. Se retira
`POST /events/{eventId}/invitations/respond` (controlador y servicio).

`guestsCount` (acompañantes) sale del contrato: el cupo cuenta personas y la spec no pide
acompañantes. Vuelve como feature si el producto lo pide.

**Justificación:** una sola forma de responder (decisión D-01 de los leads). `200` y no
`201` porque la operación es un upsert.

**Alternativas:** `PUT /events/{eventId}/rsvp/me` (más REST, pero cambia la ruta del
inventario y el código de la app sin beneficio claro).

## R3 · Acceso a eventos privados (FR-014, FR-018)

**Decisión:** puede responder quien cumpla una de estas condiciones:
1. el evento es público (`settings.isPublic`);
2. tiene invitación al evento (por la app o por enlace) que no esté vencida, o ya aceptada.

Sin acceso → `404 NOT_FOUND` con el mismo mensaje que un evento inexistente (no revela que
el evento existe). El organizador no responde a su propio evento (`409`, comportamiento
actual). Evento `CANCELLED` o `COMPLETED`, o con `endDate` pasada → `409 CONFLICT`.

**Alternativas:** `403` para privados sin invitación (revela su existencia).

## R4 · Enlaces de invitación: token y almacenamiento (FR-001 a FR-008)

**Decisión:** token de 16 bytes de `SecureRandom` en base64url sin relleno (22 caracteres,
128 bits). Se guarda en claro en la colección `invite_links` con índice único, porque el
organizador tiene que poder volver a copiar el enlace (`listInviteLinks` devuelve `url`).
La `url` se arma con `INVITE_LINK_BASE_URL` + `/i/` + token; la variable es obligatoria en
`prod` (el servicio no arranca sin ella, Principio III) y en local vale
`http://localhost:8080`. `uses` se incrementa con `$inc` condicional
(`maxUses == null || uses < maxUses`, `revoked: false`, `expiresAt > now`), atómico como R1.

El token nunca se registra en logs (Principio VI): los logs usan el `linkId`.

**Justificación:** el riesgo de guardar el token en claro es el mismo que el de la propia
URL compartida por WhatsApp; el enlace vence (máx. 30 días), se revoca y solo lo ve el
organizador. 128 bits hacen inviable adivinarlo; el límite de intentos (R6) frena el barrido.

**Alternativas:**
- *Guardar solo el hash y mostrar la URL una vez:* obliga a crear un enlace nuevo cada vez
  que el organizador lo pierde; peor UX para un riesgo que ya acota la vigencia.
- *Token firmado (JWT) sin estado:* no se puede revocar ni contar usos sin estado.

## R5 · Ruta pública de la vista previa en el gateway (FR-003, FR-009, Principio III)

**Hallazgo:** `JwtAuthenticationFilter.PUBLIC_PATHS` compara con `startsWith`. Agregar
`/api/v1/invite-links/` haría pública también `POST /invite-links/{token}/accept`.

**Decisión:** el filtro gana una lista de rutas públicas por **método + patrón exacto**:
`GET /api/v1/invite-links/{token}` (un solo segmento tras `invite-links/`). `accept` sigue
autenticada. Nueva ruta del gateway `/api/v1/invite-links/**` → `event-service`, con
`RequestRateLimiter` por IP para la vista previa (10/min, ráfaga 20) y el límite por usuario
de T-B23 para `accept`. Las respuestas `429` llevan `Retry-After` y el envelope de ADR-003.
En `event-service`, `SecurityConfig` permite sin autenticación solo `GET /api/v1/invite-links/*`.

**Alternativas:** servir la vista previa desde Cloudflare Pages con datos embebidos (no hay
SSR; obligaría a exponer otro origen).

## R6 · Nombres para mostrar sin leer la base de otro servicio (FR-003, FR-024, Principio II)

**Hallazgo:** `event-service` no conoce nombres de usuario. La vista previa necesita
`organizerDisplayName` y la lista de asistentes del organizador necesita algo legible. El
Principio II prohíbe leer `vito_auth`/`vito_social`, y `searchUsers` (Ola 2) aún no existe.

**Decisión:** `auth-service` agrega el claim `name` (el `displayName` del usuario) al access
token; el gateway lo reenvía como `X-User-Name` (se suma a la tabla de cabeceras de
CONVENTIONS y a la higiene de `X-User-*` de T-B01). `event-service` lo **desnormaliza** al
escribir: `organizerDisplayName` en el evento al crearlo o editarlo, y `displayName` en el
RSVP al responder. Eventos existentes sin el dato: la vista previa usa "El organizador" hasta
la siguiente edición. La lista de asistentes muestra `displayName` y `userId`; las
invitaciones pendientes por la app muestran solo `inviteeId` hasta que la persona responde.

**Justificación:** sin llamadas síncronas nuevas entre servicios ni lectura de bases ajenas;
el dato llega firmado en el token. El cambio de nombre de un usuario no se propaga a lo ya
escrito: aceptable para el MVP (se resuelve cuando `social-service` consuma `user-events` en
la Ola 2).

**Alternativas:**
- *Llamada síncrona `event-service` → `auth-service`:* acopla, suma latencia y un circuit
  breaker más, y requiere una operación interna nueva.
- *Que la app resuelva nombres:* no hay operación para hacerlo hasta la Ola 2.

## R7 · Contador de confirmados para no organizadores (FR-024a, decisión de los leads)

**Hallazgo:** el contrato ya expone `Event.stats` completo (`totalInvited`, `totalDeclined`,
`totalMaybe`…) a cualquiera que vea el evento.

**Decisión:** quien no es el organizador recibe `stats` con **solo** `totalAccepted` (los
demás campos se omiten); el organizador recibe `stats` completo. Se aplica en el mismo punto
que ya filtra la dirección (`hideAddressUntilAccepted`) y en todos los listados. El contrato
documenta la regla en la descripción de `EventStats` y lleva un fixture de cada vista.

**Justificación:** cumple la decisión (contador sin identidades) y cierra una exposición
que hoy existe sin que nadie la haya pedido (cuántos dijeron que no).

**Alternativas:** un campo nuevo `confirmedCount` en la raíz (contradice CONVENTIONS, que
pide todo dentro de `details`/`settings`/`stats`).

## R8 · Editar: `PATCH` en lugar de `PUT` (FR-020, FR-021)

**Decisión:** `updateEvent` es `PATCH /api/v1/events/{eventId}` con
`application/merge-patch+json` sobre `details` y `settings` (`stats`, `status`,
`organizerId` e `id` no se editan; si llegan → 400). Validaciones iguales a `createEvent`
(T-B29) más: `endDate > startDate`, `maxAttendees` en 0 o ≥ `stats.totalAccepted` (si no,
`409` con mensaje claro), evento `CANCELLED`/`COMPLETED` → `409`. Responde `200` con el
evento completo. El inventario decía `PUT`; el `@PutMapping` actual pasa a `@PatchMapping`.

**Justificación:** la spec pide enviar solo lo que cambia; `PUT` implica reemplazo total y
obligaría a la app a reenviar todo el evento.

**Alternativas:** `PUT` con semántica parcial (contradice HTTP y confunde a quien lea el contrato).

## R9 · Cancelar con `DELETE` (FR-022)

**Decisión:** `DELETE /api/v1/events/{eventId}` (`cancelEvent`) pasa el evento a
`CANCELLED` y responde `204`. Idempotente: cancelar un cancelado → `204`. Un evento
`COMPLETED` → `409`. No toca invitaciones, RSVP ni enlaces; las operaciones que exigen
evento vigente los rechazan. `scope=upcoming` e `invited` ya filtran por `PUBLISHED`.

**Alternativas:** `POST /events/{id}/cancel` (más explícito, pero el inventario y el código
ya usan `DELETE` y los leads lo aprobaron así).

## R10 · `listEvents?scope=invited` (FR-013, ADR-014)

**Decisión:** el enum de `scope` pasa a `mine | upcoming | invited`. `invited` devuelve los
eventos con invitación `PENDING` (no vencida) o `ACCEPTED` de quien llama, `PUBLISHED` y con
`endDate` futura, por `details.startDate` ascendente, paginado. Requiere una consulta a
`invitations` por `inviteeId` (índice `{inviteeId, status}`) y luego a `events` por
`_id $in` con el filtro de fecha. Es una enmienda a ADR-014 (se registra en el ADR al
mergear el contrato).

## R11 · `GET /api/v1/auth/me` (FR-025, ADR-004)

**Decisión:** devuelve el mismo `AuthUser` que `AuthResponse.user` (ADR-004), `200`; `401`
con envelope si el token no es válido o está revocado (la revocación ya la aplica el gateway,
T-B21). Ya existe en `AuthController`; se alinea al schema y se agrega fixture.

## R12 · Eventos de dominio en Kafka (FR-029, Principio V)

**Hallazgo:** `KafkaEventPublisher` publica `EVENT_CREATED`, `EVENT_UPDATED`,
`EVENT_CANCELLED`, `RSVP_CHANGED` e `INVITATION_SENT` con payload ad hoc, sin sobre
versionado, sin outbox y sin consumidores. Ningún evento Kafka está en `asyncapi.yaml`.

**Decisión:** esta ola **no** introduce el outbox ni declara los eventos Kafka; mantiene los
tipos existentes sin tipos nuevos. Unirse por enlace publica `RSVP_CHANGED`, como cualquier
respuesta, y de ese evento saldrá el ticket QR en la Ola 2. Sí corrige el "dispara y olvida": el
resultado del `send` se registra con `whenComplete` (log de error con `eventId` y tipo). La
declaración en AsyncAPI y el outbox quedan como desviación registrada en *Complexity
Tracking*, a resolver con T-C05 y antes de que exista el primer consumidor (Ola 2).

**Justificación:** sin consumidores, el outbox no protege nada todavía; meterlo aquí duplica
el tamaño de la ola.

## R13 · Enlace en la web y en el móvil (US1, SC-002)

**Decisión:** la URL pública es `https://<dominio de la app>/i/{token}`. Cloudflare Pages ya
responde todas las rutas con `index.html` (`_redirects`: `/* /index.html 200`), así que basta
con que `AppRouter.generateRoute` reconozca `/i/<token>`: muestra la vista previa (sin sesión)
y, tras iniciar sesión o registrarse, vuelve a la invitación y llama a `acceptInviteLink`.
App Links / Universal Links en Android e iOS quedan fuera (el enlace abre la web); van con
la publicación en tiendas (D-10).

## R14 · Pruebas (Principio IV) y medición (Principio VII)

**Decisión:**
- **TDD** en cupo, lista de espera, RSVP y unión por enlace (lógica crítica).
- **Testcontainers (MongoDB)** en `event-service`, que hoy no lo tiene: prueba de
  concurrencia con 50 hilos, cupo 20 (SC-003), y del `$inc` condicional de `uses`.
- **Contrato:** fixtures por operación (éxito y errores) en `docs/api/fixtures/events/` y
  `fixtures/auth/getCurrentUser.*`; tests Dart de serialización (T-C04) y tests de
  controlador contra fixtures en `event-service` y `auth-service`.
- **ArchUnit:** no está en `event-service`; queda fuera de esta ola (es transversal).
- **k6:** escenario `backend/load-tests/` de RSVP concurrente (150 asistentes, Fase
  Tracción) y de lectura de vista previa; objetivo < 300 ms p95 en lectura y < 1 s en
  RSVP/unión (SC-008).
