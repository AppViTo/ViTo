---

description: "Tareas de la Ola 1 del contrato: eventos, invitaciones y RSVP"
---

# Tasks: Ola 1 del contrato — eventos, invitaciones y RSVP

**Input**: Design documents from `/specs/003-ola-1-eventos-invitaciones/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ola-1-operations.md, quickstart.md

**Tests**: obligatorios (Principio IV). TDD en cupo, lista de espera, RSVP y unión por enlace:
la prueba se escribe primero y se ve fallar.

**Organization**: por historia de usuario. Cada tarea lleva su **dueño** al inicio de la
descripción, según CODEOWNERS:

- **[Alex]** = agente de Alex Villa: contrato (`docs/api/**`, ADR de contrato) y app Flutter
  (`frontend/**`).
- **[Bernardo]** = sesión de backend de Bernardo: `backend/**`, infraestructura y CI.
- 👤 = tarea manual de un lead (aprobaciones, secretos). El agente la prepara y verifica,
  pero no ejecuta ni ve secretos.

**Rutas abreviadas:** `ES/` = `backend/event-service/src/main/java/com/vito/event/`,
`ES-test/` = `backend/event-service/src/test/java/com/vito/event/`,
`APP/` = `frontend/vito_app/lib/`, `APP-test/` = `frontend/vito_app/test/`,
`FX/` = `docs/api/fixtures/`. Fixtures con el nombre `<operationId>[.<variante>].<status>.json`
(convención de `FX/README.md`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: historia de la spec (US1–US7)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: aprobaciones y preparación que no cambian comportamiento

- [ ] T001 👤 [Bernardo] y [Alex] Aprobar D-08 de la Ola 1 con el alcance de la spec (8 operaciones del inventario + enlaces y `scope=invited` del RFC #204, sin `searchUsers`) y registrarlo en `docs/roadmap/decisions-pending.md` (fila D-08) y en el encabezado de `docs/roadmap/endpoint-inventory.md`
- [ ] T002 [Alex] Abrir el issue `contract-change` de la Ola 1 enlazando esta spec y el RFC #204; comentar en #204 que su parte de la Ola 1 entra aquí y `searchUsers` pasa a la Ola 2. Crear la rama `contract/ola-1` desde `develop`
- [ ] T003 [P] [Bernardo] Agregar Testcontainers MongoDB (`org.testcontainers:testcontainers-junit-jupiter` y `org.testcontainers:testcontainers-mongodb`, nomenclatura de Testcontainers 2 como en `auth-service`; versión gestionada por el BOM de Spring Boot) con scope `test` en `backend/event-service/pom.xml`, y una clase base `ES-test/support/MongoContainerTest.java` con `@ServiceConnection`, siguiendo el patrón de `backend/auth-service`
- [ ] T004 [P] [Bernardo] Agregar `INVITE_LINK_BASE_URL` (R4): `vito.invite-links.base-url: ${INVITE_LINK_BASE_URL:http://localhost:8080}` en `backend/event-service/src/main/resources/application.yml`, sin default en el perfil `prod` (el servicio no arranca si falta); la variable en `.env.example`, `docker-compose.yml` y `docker-compose.prod.yml` (`${INVITE_LINK_BASE_URL:?}`) y en `infrastructure/deploy/tests/fixtures/` para el job Prod Startup
- [ ] T005 👤 [Bernardo] Agregar `INVITE_LINK_BASE_URL=https://<dominio de la app>` a `DOTENV_PRODUCTION` en los tres Environments (`production`, `production-hotfix`, `production-preflight`) antes del primer release que incluya la ola

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el contrato completo de la ola (Principio I) y las piezas que usan varias historias.

**⚠️ CRITICAL**: ninguna historia se implementa en backend ni en la app hasta que el PR de
contrato (T006–T013) esté mergeado.

### Contrato (un solo PR en `contract/ola-1`, dueño Alex)

- [ ] T006 [Alex] Schemas en `docs/api/openapi.yaml` según `contracts/ola-1-operations.md`: `Rsvp` (`status` con el enum de CONVENTIONS §6, `message` ≤ 280 nullable, `respondedAt`, `waitlistedAt` nullable, sin `guestsCount` ni QR), `RespondRsvpRequest` (`status` ∈ `ACCEPTED | DECLINED | MAYBE`, requerido), `Attendee` (`userId`, `displayName` nullable, `status`, `respondedAt`; sin correo ni teléfono), `UpdateEventRequest` (subconjunto de `details` y `settings`, todo opcional, `minProperties: 1`, `additionalProperties: false`), y `Invitation` (+ `inviteLinkId` nullable), `SendInvitationRequest`, `InviteLink`, `CreateInviteLinkRequest` (`expiresInDays` 1–30, default 7; `maxUses` 1–1000 nullable) e `InviteLinkPreview` como en el RFC #204
- [ ] T007 [Alex] Descripción de `EventStats` en `docs/api/openapi.yaml`: quien no es el organizador recibe solo `totalAccepted` (R7)
- [ ] T008 [Alex] Paths de `event-service` en `docs/api/openapi.yaml` con `operationId`, ejemplos y `ErrorResponse` en cada 4xx/5xx, según la tabla de `contracts/ola-1-operations.md`: `updateEvent` (`PATCH`, `application/merge-patch+json`), `cancelEvent`, `respondRsvp`, `listEventAttendees`, `sendInvitation`, `listEventInvitations`, `createInviteLink`, `listInviteLinks`, `revokeInviteLink`, `getInviteLinkPreview` (`security: []`) y `acceptInviteLink`
- [ ] T009 [Alex] `listEvents` en `docs/api/openapi.yaml`: enum de `scope` = `mine | upcoming | invited`, con la descripción de `invited` de R10
- [ ] T010 [Alex] `getCurrentUser` (`GET /api/v1/auth/me`) en `docs/api/openapi.yaml`, respuesta `AuthUser` (ADR-004) y `401`
- [ ] T011 [Alex] Fixtures en `FX/events/` y `FX/authentication/` según `FX/README.md` (un `.request.json` por cada `requestBody` y un fixture por cada status declarado), con estas variantes obligatorias: `respondRsvp.waitlisted.200.json`, `respondRsvp.private.404.json`, `getInviteLinkPreview.404.json`, `acceptInviteLink.max-uses.409.json`, `listEventAttendees.403.json`, `updateEvent.capacity.409.json`, `listEvents.invited.200.json`, `getEvent.organizer.200.json`, `getEvent.guest.200.json` (stats solo con `totalAccepted`) y `getCurrentUser.200.json`. `npm run contract` en verde
- [ ] T012 [P] [Alex] `docs/api/CONVENTIONS.md`: fila `X-User-Name` (claim `name`) en la tabla de cabeceras, con la regla de que el gateway descarta la del cliente (R6)
- [ ] T013 [P] [Alex] Enmiendas: `docs/decisions/adr-014-*.md` (*Actualizaciones*: `scope=invited`) y `docs/decisions/adr-016-*.md` (fila "Invitación vs RSVP" cerrada: se fusiona en `/rsvp`, D-01); en `docs/roadmap/endpoint-inventory.md`, las operaciones de la Ola 1 pasan de `PUBLICAR` a `CONTRATO (Ola 1)`, `PUT /events/{eventId}` se corrige a `PATCH` (R8), se agregan las 5 rutas de enlaces y `invitations/respond` queda como `FUSIONAR→/rsvp`

### App: modelos y pruebas de contrato (tras T011, dueño Alex)

- [ ] T014 [P] [Alex] Modelos Dart en `APP/features/events/data/models/`: `rsvp_model.dart` (sin `guestsCount`), `invitation_model.dart` (+ `inviteLinkId`), `invite_link_model.dart`, `invite_link_preview_model.dart`, `attendee_model.dart`; `event_model.dart` tolera `stats` reducido. Fechas con `.toLocal()` (Bug-B01)
- [ ] T015 [Alex] Pruebas de serialización contra los fixtures nuevos en `APP-test/contract/event_contract_test.dart` y `APP-test/contract/auth_contract_test.dart`

### Backend transversal (tras el merge del contrato, dueño Bernardo)

- [ ] T016 [P] [Bernardo] Claim `name` (= `displayName` del usuario) en el access token en `backend/auth-service/src/main/java/com/vito/auth/application/service/TokenProvider.java`, con prueba unitaria
- [ ] T017 [Bernardo] Gateway: agregar `X-User-Name` a `USER_HEADERS` (se descarta la del cliente) y reenviarla desde el claim `name` en `backend/api-gateway/src/main/java/com/vito/gateway/security/JwtAuthenticationFilter.java`; prueba de que un `X-User-Name` del cliente nunca llega al servicio (T-B01)
- [ ] T018 [Bernardo] Gateway: reemplazar el `startsWith` de `PUBLIC_PATHS` por una lista de reglas método + patrón exacto (R5) en `JwtAuthenticationFilter.java`; agregar `GET /api/v1/invite-links/{token}` (un solo segmento). Pruebas: `POST /api/v1/invite-links/x/accept` sin token → 401; `GET /api/v1/invite-links/x` sin token → reenviado; login, register y refresh siguen públicos
- [ ] T019 [Bernardo] Gateway: ruta `/api/v1/invite-links/**` → `event-service` en `backend/api-gateway/src/main/resources/application.yml`, con `RequestRateLimiter` por IP para la vista previa (10/min, ráfaga 20) y el límite por usuario de T-B23 para `accept`; `429` con `Retry-After` y envelope ADR-003
- [ ] T020 [P] [Bernardo] Modelo de dominio según `data-model.md`: `Event.organizerDisplayName` (nullable), `Invitation.inviteLinkId` (nullable), `RSVP.displayName` (nullable) y `RSVP.waitlistedAt` (nullable), y quitar `guestsCount` de la entrada; nuevo `ES/domain/model/InviteLink.java` (`token` de 22 caracteres base64url, `expiresAt`, `maxUses` 1–1000 nullable, `uses` ≥ 0, `revoked`, `createdBy`, `createdAt`). Todas las fechas de `Invitation` (`sentAt`, `expiresAt`, `respondedAt`), `RSVP` (`respondedAt`, `waitlistedAt`) e `InviteLink` como `Instant`, como ya es `Event` desde Bug-B01 (FR-028); prueba de serialización con sufijo `Z` en `ES-test/domain/model/`
- [ ] T066 [Bernardo] Migración de datos de una sola vez (Principio IX), **antes** de T021: `ES/infrastructure/migration/Ola1DataMigration.java`, idempotente y registrada en una colección `migrations` para no repetirse. (1) Detecta duplicados `{eventId, userId}` en `rsvps` y `{eventId, inviteeId}` en `invitations`, conserva el más reciente y registra cuántos quitó (sin datos personales); (2) recalcula `stats.totalAccepted`, `totalDeclined`, `totalMaybe`, `totalWaitlisted` y `totalInvited` de cada evento desde `rsvps` e `invitations`; (3) completa `waitlistedAt` con `respondedAt` en los `WAITLISTED`; (4) convierte a `Date` UTC las fechas guardadas como texto local si las hay. Prueba con Testcontainers en `ES-test/infrastructure/migration/Ola1DataMigrationIT.java`
- [ ] T021 [Bernardo] Índices declarados en código (Principio VII): único `{eventId, userId}` en `rsvps`, `{eventId, status, waitlistedAt}` en `rsvps`, único `{eventId, inviteeId}` y `{inviteeId, status}` en `invitations`, único `{token}` y `{eventId, revoked, expiresAt}` en `invite_links`, en los adaptadores de `ES/infrastructure/adapter/outbound/persistence/`
- [ ] T022 [Bernardo] Prueba primero (TDD, R1): `ES-test/infrastructure/adapter/outbound/persistence/CapacityConcurrencyIT.java` con Testcontainers: 50 hilos confirman en un evento de cupo 20 → exactamente 20 confirmados y 30 rechazados por cupo, 10 repeticiones. Debe fallar con el código actual
- [ ] T023 [Bernardo] Puerto `EventRepository` con operaciones atómicas (R1): `tryReserveSeat(eventId)` (`findAndModify` con guarda `status: PUBLISHED` y `maxAttendees == 0 || totalAccepted < maxAttendees`, `$inc +1`), `releaseSeat(eventId)` (`$inc -1`) e `incrementStat(eventId, campo, delta)`; implementar en `EventMongoAdapter.java` y retirar la reescritura completa del documento de `updateEventStats` en `ES/application/service/EventServiceImpl.java`. T022 pasa
- [ ] T024 [Bernardo] Caso de uso compartido `ES/application/service/AttendanceService.java`: `confirm(eventId, userId, displayName)` reserva lugar o deja `WAITLISTED` con `waitlistedAt`; `release(eventId, userId)` libera y promueve al `WAITLISTED` más antiguo (si la reserva falla, vuelve a `WAITLISTED`). Pruebas unitarias primero en `ES-test/application/service/AttendanceServiceTest.java`
- [ ] T025 [P] [Bernardo] Tomar `X-User-Name` en `ES/infrastructure/security/JwtAuthenticationFilter.java` y exponerlo junto con el userId a los controladores; `organizerDisplayName` se guarda al crear el evento
- [ ] T026 [P] [Bernardo] `ES/infrastructure/adapter/outbound/messaging/KafkaEventPublisher.java`: registrar el resultado de cada `send` con `whenComplete` (error con `eventId` y tipo, sin datos personales) (R12)
- [ ] T067 [P] [Bernardo] Redacción del token del enlace fuera de la app (Principio VI, R4): en `api-gateway` y `event-service`, las trazas OpenTelemetry usan la plantilla `/api/v1/invite-links/{token}` en lugar de la ruta real (`url.path`/`http.route`), y ningún log de acceso ni de error escribe la ruta con el token (`backend/api-gateway/src/main/java/com/vito/gateway/` y `ES/infrastructure/config/`). Prueba que verifica que el token no aparece en los atributos del span ni en la salida del log
- [ ] T027 [Bernardo] Vista según quién consulta (R7): quien no es organizador recibe `stats` con solo `totalAccepted`, en el mismo punto que aplica `hideAddressUntilAccepted` (`getEvent` y todos los `scope` de `listEvents`) en `ES/application/service/EventServiceImpl.java`; pruebas contra `getEvent.organizer.200.json` y `getEvent.guest.200.json`

**Checkpoint**: contrato mergeado, mock de Prism con las 13 operaciones, cupo atómico verificado.

---

## Phase 3: User Story 3 - Los invitados responden si van (Priority: P1) 🎯 MVP

**Goal**: RSVP como única forma de responder, con cupo atómico y lista de espera (FR-014 a FR-019).
Va primero porque US1 reutiliza su lógica de confirmación.

**Independent Test**: quickstart §3 (cupo 2, tres confirmaciones, promoción al liberar, privado sin invitación → 404).

### Tests for User Story 3

- [ ] T028 [P] [US3] [Bernardo] Pruebas primero en `ES-test/application/service/RespondRsvpTest.java`: upsert idempotente (FR-019), cambio de respuesta sin error, `WAITLISTED`/`PENDING` como entrada → 400, privado sin invitación → 404 igual al inexistente (R3), organizador → 409, evento cancelado, terminado o con `endDate` pasada → 409, invitación `PENDING` → `ACCEPTED`/`DECLINED`/sigue `PENDING` según la respuesta (FR-017); quien ya tiene un RSVP conserva el acceso a un evento privado aunque su invitación haya vencido (aclaración del 2026-11-01)
- [ ] T029 [P] [US3] [Bernardo] Prueba de controlador contra `FX/events/respondRsvp.*` en `ES-test/infrastructure/adapter/inbound/rest/RespondRsvpContractTest.java`

### Implementation for User Story 3

- [ ] T030 [US3] [Bernardo] Reescribir `respondToRSVP` como upsert sobre `AttendanceService` (T024) en `ES/application/service/EventServiceImpl.java`: entrada `ACCEPTED | DECLINED | MAYBE`, `message` ≤ 280, guarda `displayName`, actualiza la invitación en la misma operación, publica `RSVP_CHANGED`; deja de generar el QR (pasa a `checkin-service` en la Ola 2)
- [ ] T031 [US3] [Bernardo] `POST /api/v1/events/{eventId}/rsvp` en `ES/infrastructure/adapter/inbound/rest/EventController.java`: `200 Rsvp`; borrar `POST /{eventId}/invitations/respond` del controlador, del puerto `EventService` y de `respondToInvitation` (D-01, FR-030)
- [ ] T032 [P] [US3] [Alex] Caso de uso y repositorio: ajustar `APP/features/events/domain/usecases/respond_rsvp.dart` y `event_repository_impl.dart` a `RespondRsvpRequest`/`Rsvp` (sin `guestsCount`)
- [ ] T033 [US3] [Alex] UI de RSVP en `APP/features/events/presentation/pages/event_detail_page.dart`: "Voy", "No voy", "Tal vez", estado actual, aviso "Estás en lista de espera" cuando la respuesta es `WAITLISTED`, y botones deshabilitados en eventos cancelados o pasados; textos en español (Bug-F10). Test de bloc en `APP-test/features/events/`

**Checkpoint**: US3 funciona sola contra el backend real y el mock.

---

## Phase 4: User Story 1 - El organizador comparte un enlace y sus invitados se unen (Priority: P1)

**Goal**: enlaces de invitación con vista previa pública y unión idempotente (FR-001 a FR-009, FR-013). Cierra el enlace de Bug-F09.

**Independent Test**: quickstart §2.

### Tests for User Story 1

- [ ] T034 [P] [US1] [Bernardo] Pruebas primero en `ES-test/application/service/InviteLinkServiceTest.java`: token de 22 caracteres base64url distinto en cada llamada; vigencia 1–30 (default 7) y `maxUses` 1–1000; vencido, revocado e inexistente → la misma excepción de 404 (FR-006); límite alcanzado o evento no vigente → 409; unión idempotente sin sumar uso; organizador → éxito sin crear nada; revocar no afecta a quien ya se unió
- [ ] T035 [P] [US1] [Bernardo] Prueba de integración en `ES-test/infrastructure/adapter/outbound/persistence/InviteLinkMongoAdapterIT.java`: el `$inc` condicional de `uses` nunca supera `maxUses` con 20 uniones simultáneas
- [ ] T036 [P] [US1] [Bernardo] Pruebas de controlador contra `FX/events/createInviteLink.*`, `listInviteLinks.*`, `revokeInviteLink.*`, `getInviteLinkPreview.*`, `acceptInviteLink.*` y `listEvents.invited.200.json` en `ES-test/infrastructure/adapter/inbound/rest/InviteLinkContractTest.java`

### Implementation for User Story 1

- [ ] T037 [P] [US1] [Bernardo] Puerto `ES/domain/port/outbound/InviteLinkRepository.java` y adaptador `ES/infrastructure/adapter/outbound/persistence/InviteLinkMongoAdapter.java` (búsqueda por token, listado por evento con vigentes primero, `incrementUses` condicional `revoked: false`, `expiresAt > now`, `maxUses == null || uses < maxUses`)
- [ ] T038 [US1] [Bernardo] `ES/application/service/InviteLinkService.java`: crear (128 bits de `SecureRandom`, base64url), listar, revocar (idempotente), vista previa (sin dirección ni coordenadas; `organizerDisplayName` o "El organizador") y unir (crea la invitación con `inviteLinkId` y llama a `AttendanceService.confirm`; publica `RSVP_CHANGED`). Arma `url` con `vito.invite-links.base-url` + `/i/` + token. El token nunca va a los logs, solo el `linkId`
- [ ] T039 [US1] [Bernardo] `ES/infrastructure/adapter/inbound/rest/InviteLinkController.java` con las 5 operaciones, solo el organizador en las 3 de gestión (403 si no); `ES/infrastructure/config/SecurityConfig.java` permite sin autenticación solo `GET /api/v1/invite-links/*`
- [ ] T040 [US1] [Bernardo] `scope=invited` (R10) en `ES/domain/port/inbound/EventService.java` (`ListScope`), `EventServiceImpl.listEvents` y `EventMongoAdapter`: invitaciones `PENDING` no vencidas o `ACCEPTED` de quien llama, o RSVP suyo distinto de `DECLINED` → eventos `PUBLISHED` con `endDate` futura por `details.startDate` ascendente, paginado, con `hideAddressUntilAccepted` y `stats` reducido
- [ ] T041 [P] [US1] [Alex] Datasource, repositorio y casos de uso `create_invite_link.dart`, `list_invite_links.dart`, `revoke_invite_link.dart`, `get_invite_link_preview.dart` y `accept_invite_link.dart` en `APP/features/events/` (`data/` y `domain/usecases/`), registrados en `get_it`
- [ ] T042 [US1] [Alex] En `APP/features/events/presentation/pages/create_event_screen.dart` y en el detalle del evento: al crear (o desde el detalle), generar el enlace y conectar "Copiar enlace de invitación" y "Enviar por WhatsApp" (`SharePlus.instance.share`) a la `url` real; pantalla de gestión de enlaces del organizador (usos, vencimiento, revocar). Quita el estado provisional de Bug-F09
- [ ] T043 [US1] [Alex] Ruta `/i/<token>` en `APP/config/routes/app_router.dart` y página `APP/features/events/presentation/pages/invite_link_page.dart`: vista previa sin sesión, "Inicia sesión o regístrate para unirte", vuelta a la invitación tras el login o el registro y llamada a `acceptInviteLink`; mensaje único "El enlace no existe o ya no está disponible" en 404
- [ ] T044 [US1] [Alex] Sección "Invitaciones" (o integración en "Próximos eventos") con `scope=invited` en el home (`APP/features/home/`), con estado vacío en español

**Checkpoint**: US1 y US3 funcionan solas; el enlace se comparte, se abre en la web y confirma.

---

## Phase 5: User Story 2 - El organizador edita su evento (Priority: P1)

**Goal**: editar con `PATCH` (FR-020, FR-021, FR-023). Cierra Bug-F07.

**Independent Test**: quickstart §4, pasos 1–3.

### Tests for User Story 2

- [ ] T045 [P] [US2] [Bernardo] Pruebas en `ES-test/application/service/UpdateEventTest.java`: solo cambian los campos enviados; mismas validaciones que `createEvent` (T-B29); `endDate > startDate`; `maxAttendees` por debajo de `totalAccepted` → 409; `stats`, `status`, `id` u `organizerId` → 400; otro usuario → 403; cancelado o terminado → 409
- [ ] T046 [P] [US2] [Bernardo] Prueba de controlador contra `FX/events/updateEvent.*` en `ES-test/infrastructure/adapter/inbound/rest/UpdateEventContractTest.java`

### Implementation for User Story 2

- [ ] T047 [US2] [Bernardo] `@PutMapping` → `@PatchMapping(consumes = "application/merge-patch+json")` en `EventController.java` y merge parcial en `EventServiceImpl.updateEvent` (actualiza `organizerDisplayName`, publica `EVENT_UPDATED`)
- [ ] T048 [P] [US2] [Alex] Caso de uso `APP/features/events/domain/usecases/update_event.dart` enviando solo los cambios, y su método en el datasource y el repositorio
- [ ] T049 [US2] [Alex] Pantalla `APP/features/events/presentation/pages/edit_event_page.dart` (fecha, foto, descripción, lugar, cupo, visibilidad, ocultar dirección), accesible solo para el organizador desde el detalle; errores 409/400 con el mensaje del backend. Test de widget en `APP-test/features/events/`

**Checkpoint**: Bug-F07 cerrado.

---

## Phase 6: User Story 4 - El organizador invita a usuarios de ViTo y ve a quién invitó (Priority: P2)

**Goal**: invitaciones por la app, solo el organizador las ve (FR-010 a FR-012).

**Independent Test**: quickstart §5, pasos 1–2.

- [ ] T050 [P] [US4] [Bernardo] Pruebas en `ES-test/application/service/InvitationTest.java` y contra `FX/events/sendInvitation.*` y `listEventInvitations.*`: 403 a quien no es organizador en ambas (corrige el hallazgo del #204), 409 si ya está invitado, `EXPIRED` calculado al leer, `totalInvited` con `$inc`
- [ ] T051 [US4] [Bernardo] Verificar organizador en `getEventInvitations`, `message` ≤ 280, `expiresAt` = envío + 7 días y `incrementStat` en `EventServiceImpl.sendInvitation`/`getEventInvitations`; ajustar `EventController.java` a los códigos del contrato
- [ ] T052 [P] [US4] [Alex] Casos de uso `send_invitation.dart` y `list_event_invitations.dart` en `APP/features/events/domain/usecases/` y lista de invitaciones del organizador (estado, origen app o enlace, fechas) en el detalle del evento. La búsqueda de invitados sigue con el aviso de Bug-F08 hasta la Ola 2

---

## Phase 7: User Story 5 - El organizador cancela su evento (Priority: P2)

**Goal**: cancelar sin borrar (FR-022, FR-023).

**Independent Test**: quickstart §4, paso 4.

- [ ] T053 [P] [US5] [Bernardo] Pruebas en `ES-test/application/service/CancelEventTest.java` y contra `FX/events/cancelEvent.*`: `204` idempotente, `COMPLETED` → 409, otro usuario → 403; tras cancelar, RSVP, unión por enlace, invitar y `PATCH` → 409, y el evento sale de `upcoming` e `invited`
- [ ] T054 [US5] [Bernardo] `DELETE /api/v1/events/{eventId}` → `CANCELLED` sin tocar invitaciones, RSVP ni enlaces en `EventServiceImpl.cancelEvent` y `EventController.java`; publica `EVENT_CANCELLED`
- [ ] T055 [P] [US5] [Alex] Caso de uso `cancel_event.dart` y acción "Cancelar evento" con confirmación en el detalle (solo organizador); los invitados ven el evento como "Cancelado"

---

## Phase 8: User Story 6 - Ver quién asiste y cuántos van (Priority: P2)

**Goal**: lista solo del organizador y contador para los demás (FR-024, FR-024a).

**Independent Test**: quickstart §5, pasos 3–4.

- [ ] T056 [P] [US6] [Bernardo] Pruebas contra `FX/events/listEventAttendees.*`: organizador → 200 paginado con `displayName` y sin correo ni teléfono; cualquier otro, aunque esté confirmado → 403; filtro `status` opcional
- [ ] T057 [US6] [Bernardo] Autorización de organizador y mapeo a `Attendee` en `EventServiceImpl.getEventAttendees` y `EventController.java`
- [ ] T058 [P] [US6] [Alex] Lista de asistentes del organizador (`list_event_attendees.dart` y página o sección en el detalle) y contador "N personas confirmadas" para los invitados, desde `stats.totalAccepted`, en `APP/features/events/presentation/pages/event_detail_page.dart`

---

## Phase 9: User Story 7 - La app recupera mi sesión al abrirse (Priority: P3)

**Goal**: `GET /auth/me` (FR-025).

**Independent Test**: quickstart §6.

- [ ] T059 [P] [US7] [Bernardo] Alinear `AuthController.getCurrentUser` en `backend/auth-service/src/main/java/com/vito/auth/infrastructure/adapter/inbound/rest/AuthController.java` a `AuthUser` (ADR-004) y `401` con envelope; prueba contra `FX/authentication/getCurrentUser.*`
- [ ] T060 [P] [US7] [Alex] En el arranque de la app (`AuthBloc`, `APP/features/auth/`), llamar a `getCurrentUser` con la sesión guardada; si responde 401, ir al inicio de sesión

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T061 [P] [Bernardo] Escenarios k6 `backend/load-tests/ola-1-rsvp.js` (150 asistentes, RSVP concurrente, 0 cupos superados, < 1 s p95) y `backend/load-tests/ola-1-preview.js` (< 300 ms p95), siguiendo `backend/load-tests/event-load-test.js`
- [ ] T062 [P] [Bernardo] Panel de `429` por ruta (vista previa y `accept`) en el tablero del gateway de T-I13 (`infrastructure/observability/`)
- [ ] T063 [Bernardo] Registrar como tarea del backlog la desviación del Principio V (outbox, sobre versionado y declaración de los eventos Kafka de `event-service` en `asyncapi.yaml`), con dependencia de T-C05 y antes del primer consumidor (Ola 2)
- [ ] T064 [Bernardo] y [Alex] Recorrer `quickstart.md` completo contra el stack local y pegar la salida en el PR de cierre de la ola
- [ ] T065 [Alex] Cerrar T-C07 Ola 1, Bug-F07 y la parte de enlace de Bug-F09 en `docs/roadmap/tasks.md`; comentar en el RFC #204 qué quedó y qué pasa a la Ola 2

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 (D-08) bloquea el merge del contrato; T003–T004 se pueden hacer ya.
- **Foundational (Phase 2)**: T006–T013 forman un solo PR de contrato y bloquean todo lo
  demás. T014–T015 (app) y T016–T027, T066 y T067 (backend) empiezan cuando ese PR se mergea y
  avanzan en paralelo. T066 (migración) va antes de T021 (índices únicos) y se despliega con
  ellos.
- **User Stories (Phase 3+)**: dependen de la Phase 2. US3 va antes que US1 porque US1 usa
  `AttendanceService` (T024) y el RSVP; las demás son independientes entre sí.
- **Polish**: al final.

### User Story Dependencies

- **US3 (P1)**: tras la Phase 2. Base de US1.
- **US1 (P1)**: tras la Phase 2; usa T024. Su parte de app solo necesita el contrato.
- **US2 (P1)**: tras la Phase 2. Independiente.
- **US4, US5, US6 (P2)** y **US7 (P3)**: tras la Phase 2. Independientes.

### Within Each User Story

- Pruebas primero; en backend, deben fallar antes de implementar.
- Backend: dominio → puerto/adaptador → servicio → controlador.
- App: modelos (T014) → casos de uso → bloc → pantalla. La app avanza contra el mock de
  Prism (`npm run mock:api`) sin esperar al backend.

### Parallel Opportunities

- Backend (Bernardo) y app (Alex) avanzan en paralelo en todas las historias una vez mergeado
  el contrato.
- Dentro de la Phase 2: T016, T020, T025, T026 y T067 son de archivos distintos (T027 no,
  comparte `EventServiceImpl.java` con T025).
- Los IDs T066 y T067 se agregaron tras `/speckit-analyze`; van en la Phase 2 en el orden
  indicado arriba.
- Las pruebas marcadas [P] de cada historia van en paralelo.

---

## Parallel Example: User Story 1

```text
# Bernardo, en paralelo:
T034 InviteLinkServiceTest · T035 InviteLinkMongoAdapterIT · T036 InviteLinkContractTest · T037 InviteLinkRepository

# Alex, en paralelo con todo lo anterior (contra el mock):
T041 casos de uso de enlaces → T042 copiar y WhatsApp → T043 ruta /i/<token>
```

---

## Implementation Strategy

### MVP First

1. Phase 1 + contrato (T006–T013) mergeado con D-08.
2. Phase 2 backend: cupo atómico verificado (T022–T024).
3. US3 (RSVP) + US1 (enlace) + US2 (editar): cierra los hitos MVP 4 y 5 y los bugs F07 y F09.
4. **Validar** con quickstart §2–§4 y liberar.

### Incremental Delivery

1. MVP (US3, US1, US2).
2. US4 + US5 + US6 (gestión del organizador).
3. US7 (auto-login).
4. Polish: k6, tablero y quickstart completo.

### Mapeo a `docs/roadmap/tasks.md`

Se registran en `docs/roadmap/tasks.md` con este PR, con su fila en la tabla resumen y la
"Asignación del 2026-11-01 (Ola 1)":

| Backlog | Dueño | Tareas de esta feature | Estado inicial |
|---------|-------|------------------------|----------------|
| T-C10 · Contrato de la Ola 1 | Agente de Alex | T002, T006–T013, T065 | 🟡 RFC (requiere T001) |
| T-F12 · App de la Ola 1 | Agente de Alex | T014–T015, T032–T033, T041–T044, T048–T049, T052, T055, T058, T060 | 🔴 BLOQUEADA por T-C10 |
| T-B34 · `event-service` de la Ola 1 | Bernardo | T003, T020–T031, T066, T034–T040, T045–T047, T050–T051, T053–T054, T056–T057 | 🔴 BLOQUEADA por T-C10 (T003 se puede adelantar) |
| T-B35 · `auth-service` y gateway de la Ola 1 | Bernardo | T016–T019, T059, T062, T067 | 🔴 BLOQUEADA por T-C10 |
| T-I28 · `INVITE_LINK_BASE_URL` y carga de la Ola 1 | Bernardo | T004–T005, T061 | 🟢 READY (T004); T005 👤 antes del release |
| T-B36 · Outbox y AsyncAPI de los eventos de `event-service` | Bernardo | T063 | 🔴 BLOQUEADA por T-C05 |

T001 (D-08) y T064 son compartidas por los dos leads.

---

## Notes

- Cada bloque del mapeo es un PR a `develop`, sin apilar (CONTRIBUTING §2); el de contrato
  primero.
- Los IDs del backlog evitan los que ya usan PR abiertos: T-C09, T-B33 y T-F11 (#242, Ola 3)
  y T-I27 (#241).
- Commit tras cada tarea o grupo lógico; marcar `[X]` aquí y el estado en `tasks.md` del
  roadmap en el mismo PR.
