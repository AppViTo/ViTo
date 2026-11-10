---

description: "Tareas de la Ola 2a del contrato: grafo social y búsqueda de usuarios"
---

# Tasks: Ola 2a del contrato — grafo social y búsqueda de usuarios

**Input**: Design documents from `/specs/004-ola-2a-social-busqueda/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ola-2a-operations.md, quickstart.md

**Tests**: obligatorios (Principio IV). TDD en la búsqueda (rangos, tildes, privados, texto
literal) y en el follow concurrente: la prueba se escribe primero y se ve fallar.

**Organization**: por historia de usuario. Cada tarea lleva su **dueño** al inicio de la
descripción, según CODEOWNERS:

- **[Alex]** = agente de Alex Villa: contrato (`docs/api/**`, ADR de contrato, inventario) y app
  Flutter (`frontend/**`).
- **[Bernardo]** = sesión de backend de Bernardo: `backend/**`, infraestructura y CI.
- 👤 = tarea manual de un lead (aprobaciones, secretos). El agente la prepara y verifica, pero no
  ejecuta ni ve secretos.

**Rutas abreviadas:** `AS/` = `backend/auth-service/src/main/java/com/vito/auth/`,
`AS-test/` = `backend/auth-service/src/test/java/com/vito/auth/`,
`SS/` = `backend/social-service/src/main/java/com/vito/social/`,
`SS-test/` = `backend/social-service/src/test/java/com/vito/social/`,
`GW/` = `backend/api-gateway/`, `APP/` = `frontend/vito_app/lib/`,
`APP-test/` = `frontend/vito_app/test/`, `FX/` = `docs/api/fixtures/`. Fixtures con el nombre
`<operationId>[.<variante>].<status>.json` (convención de `FX/README.md`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: historia de la spec (US1–US4)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: aprobaciones y preparación que no cambian comportamiento

- [X] T001 👤 [Bernardo] y [Alex] Aprobar D-08 de la Ola 2a con el alcance de la spec (parte social del inventario + `searchUsers` del RFC #277 en `auth-service` + `getUsersByIds`; check-in/QR fuera, Ola 2b). Se aprueba con el PR de esta spec; registrarlo en `docs/roadmap/decisions-pending.md` (fila D-08: "Olas 1, 2a y 3 aprobadas") y en el encabezado de `docs/roadmap/endpoint-inventory.md`
- [ ] T002 [Alex] Comentar en el RFC #277 que entra en esta spec con las decisiones 1 (dueño `auth-service`) y 2 (privados fuera de la búsqueda) tomadas el 2026-11-09, y que se cierra con el merge del contrato. Abrir el issue `contract-change` de la Ola 2a enlazando la spec y crear la rama `contract/ola-2a` desde `develop`
- [X] T003 [P] [Bernardo] Agregar Testcontainers MongoDB (`org.testcontainers:testcontainers-junit-jupiter` y `org.testcontainers:testcontainers-mongodb`, scope `test`, versión del BOM de Spring Boot) a `backend/social-service/pom.xml`, y la clase base `SS-test/support/MongoContainerTest.java` con `@ServiceConnection`, siguiendo `backend/event-service`
- [X] T004 [P] [Bernardo] `AUTH_SERVICE_URI` para `social-service`: `auth-service.uri: ${AUTH_SERVICE_URI:http://localhost:8081}` en `backend/social-service/src/main/resources/application.yml`; la variable en `.env.example`, `docker-compose.yml` y `docker-compose.prod.yml` (`http://auth-service:8081` por el DNS de Compose) y en `infrastructure/deploy/tests/fixtures/` para el job Prod Startup

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el contrato completo de la ola (Principio I) y las piezas que usan varias historias.

**⚠️ CRITICAL**: ninguna historia se implementa en backend ni en la app hasta que el PR de
contrato (T005–T010) esté mergeado.

### Contrato (un solo PR en `contract/ola-2a`, dueño Alex)

- [ ] T005 [Alex] Schemas en `docs/api/openapi.yaml` según `contracts/ola-2a-operations.md`: `UserSummary` (`userId`, `username`, `displayName` nullable, `avatar` nullable con `format: uri`; todos requeridos; descripción "Nunca correo ni teléfono") y `SocialProfile` (`userId`, `username`, `displayName` nullable, `avatar` nullable, `followersCount` ≥ 0, `followingCount` ≥ 0, `isFollowing`, `isFollowedBy`; todos requeridos; los booleanos en `false` en el propio perfil)
- [ ] T006 [Alex] Paths de `auth-service` en `docs/api/openapi.yaml`, tag `Users`, con `operationId`, ejemplos y `ErrorResponse` en cada 4xx: `searchUsers` (`GET /api/v1/users/search`; `query` obligatorio de 2 a 50 caracteres tras quitar una `@` inicial; `page` ≥ 0, por defecto 0; `size` de 1 a 20, por defecto 20; `200` array plano; 400, 401, 429 con `Retry-After`) con las reglas de coincidencia, orden y privacidad de `contracts/ola-2a-operations.md` en la descripción; `getUsersByIds` (`GET /api/v1/users`; `ids` obligatorio, de 1 a 50 separados por coma, repetidos ignorados; `200` en el orden pedido, omite los inexistentes; 400, 401)
- [ ] T007 [Alex] Paths de `social-service` en `docs/api/openapi.yaml`, tag `Social`: `followUser` (`POST /api/v1/social/follow/{userId}`, `204` también si ya lo seguía; 400 a uno mismo, 401, 404, 503), `unfollowUser` (`DELETE`, `204` también si no lo seguía; 401, 404, 503), `getSocialProfile` (`GET /api/v1/social/profile/{userId}`; 401, 404, 503), `listFollowers` y `listFollowing` (`GET /api/v1/social/followers|following/{userId}`; `page` ≥ 0, `size` de 1 a 50, por defecto 20, con la nota de que el máximo es menor que el 100 de CONVENTIONS §5; orden por fecha de seguimiento descendente; 400, 401, 404, 503). Los `503` llevan `Retry-After`
- [ ] T008 [Alex] Fixtures en `FX/users/` y `FX/social/` según `FX/README.md` y la tabla de `contracts/ola-2a-operations.md`, con estas variantes obligatorias: `searchUsers.empty.200.json`, `searchUsers.429.json`, `getUsersByIds.200.json`, `followUser.self.400.json`, `followUser.503.json`, `getSocialProfile.200.json` (`isFollowing: true`), `getSocialProfile.own.200.json` (los dos booleanos en `false`) y `listFollowers.empty.200.json`. Ningún fixture con `email`, `phone` ni correos en ejemplos. `npm run contract` en verde
- [ ] T009 [P] [Alex] `docs/api/CONVENTIONS.md` §9: excepción de `searchUsers` (1 por segundo por usuario con ráfaga de 10, `Retry-After: 1`), junto a la de la vista previa del enlace
- [ ] T010 [P] [Alex] `docs/roadmap/endpoint-inventory.md`: las 5 rutas de social pasan a `CONTRATO (Ola 2a)`; `GET /social/search` → `RETIRAR` (la reemplaza `searchUsers` de `auth-service`); `GET /social/is-following/{userId}` → `FUSIONADA en profile.isFollowing`; filas nuevas `GET /api/v1/users/search` y `GET /api/v1/users` en la sección Auth. `docs/decisions/adr-016-*.md` (*Actualizaciones*): la búsqueda y los datos públicos por lotes son de `auth-service`; `social-service` es dueño solo del grafo

### App: modelos y pruebas de contrato (tras T008, dueño Alex)

- [ ] T011 [P] [Alex] Modelos Dart: `APP/features/social/data/models/user_summary_model.dart` (nuevo; `displayName` y `avatar` nullable) y `APP/features/social/data/models/social_profile_model.dart` con `isFollowing` e `isFollowedBy` leídos con esos nombres, sin `bio` ni campos que no estén en el schema
- [ ] T012 [Alex] Pruebas de serialización contra los fixtures nuevos en `APP-test/contract/social_contract_test.dart` y `APP-test/contract/users_contract_test.dart` (nuevos), usando `fixture_loader.dart`

### Backend transversal (tras el merge del contrato, dueño Bernardo)

- [ ] T013 [Bernardo] Prueba primero: `SS-test/infrastructure/migration/Ola2aSocialMigrationIntegrationTest.java` con Testcontainers: con relaciones duplicadas, una de alguien consigo mismo y `createdAt` como texto, la migración deja una relación por par (la más antigua), borra la propia, convierte la fecha a UTC, se registra en `migrations` y una segunda ejecución no cambia nada
- [ ] T014 [Bernardo] `SS/infrastructure/migration/Ola2aSocialMigration.java` con el patrón de `Ola1DataMigration` de `event-service` (registro en `migrations`, pasos idempotentes, logs solo con conteos), ejecutada al arrancar antes de crear índices. T013 pasa
- [ ] T015 [Bernardo] `SS/domain/model/FollowRelationship.java`: `createdAt` como `Instant`; índices declarados en `SS/infrastructure/adapter/outbound/persistence/FollowMongoAdapter.java` **después** de T014: único `{followerId: 1, followingId: 1}`, `{followingId: 1, createdAt: -1, _id: -1}` y `{followerId: 1, createdAt: -1, _id: -1}`
- [ ] T016 [Bernardo] Puerto `SS/domain/port/outbound/UserDirectory.java` (`List<UserSummary> findByIds(List<String> ids)`, en el orden pedido y sin los inexistentes) y el modelo de dominio `SS/domain/model/UserSummary.java`, sin dependencias de Spring (Principio II)
- [ ] T017 [Bernardo] Prueba primero: `SS-test/infrastructure/adapter/outbound/directory/AuthUserDirectoryAdapterTest.java` con `MockRestServiceServer`: reenvía el `Authorization` de la petición en curso, arma `?ids=` y conserva el orden; respuesta vacía → lista vacía; timeout o 5xx → excepción de "servicio no disponible"; `401` → excepción de no autenticado
- [ ] T018 [Bernardo] `SS/infrastructure/adapter/outbound/directory/AuthUserDirectoryAdapter.java` con `RestClient` (timeout de conexión de 1 s y de lectura de 2 s, research R2), siguiendo `EventServiceOrganizerAdapter` de `mosaic-service`. Sin llamada si la lista de ids está vacía. T017 pasa
- [ ] T019 [Bernardo] `SS/infrastructure/adapter/inbound/rest/GlobalExceptionHandler.java`: "servicio no disponible" → `503 SERVICE_UNAVAILABLE` con envelope ADR-003 y `Retry-After: 5`; no autenticado → `401`. Prueba en `SS-test/infrastructure/adapter/inbound/rest/GlobalExceptionHandlerTest.java`
- [ ] T020 [P] [Bernardo] `SS/infrastructure/adapter/outbound/messaging/KafkaEventPublisher.java`: registrar el resultado de cada `send` con `whenComplete` (error con el tipo de evento, sin ids de personas) (research R10)

### `getUsersByIds` en `auth-service` (requisito de US2–US4, dueño Bernardo)

IDs fuera de secuencia: van después de T020 en el orden de ejecución. Comparten controlador,
puerto y servicio con US1 (T030–T031); si US1 se hace primero, se agregan sobre lo suyo.

- [ ] T061 [Bernardo] Prueba primero, en `AS-test/infrastructure/adapter/inbound/rest/UserControllerContractTest.java`: `getUsersByIds` con orden pedido, inexistentes omitidos, privados incluidos, repetidos ignorados, `400` con 0 o 51 ids (validación "`ids` de `getUsersByIds` de 1 a 50"), `401`, y ninguna respuesta con `email` ni `phone`
- [ ] T062 [Bernardo] `UserRepository.findAllByIds(ids)` en `AS/infrastructure/adapter/outbound/persistence/UserMongoAdapter.java` y `GET /api/v1/users?ids=` en `AS/infrastructure/adapter/inbound/rest/UserController.java` y `AS/application/service/UserDirectoryServiceImpl.java`, devolviendo `UserSummary` en el orden pedido; `/api/v1/users/**` autenticado en `AS/infrastructure/config/SecurityConfig.java`. T061 pasa

**Checkpoint**: contrato mergeado, mock de Prism con las 7 operaciones, `social-service` con
índices y `UserDirectory`.

---

## Phase 3: User Story 1 - El organizador encuentra a quién invitar (Priority: P1) 🎯 MVP

**Goal**: buscar usuarios registrados por usuario o nombre, sin tildes, desde "¿A quién invitamos?"
(cierra Bug-F08).

**Independent Test**: con dos cuentas, A escribe dos letras del nombre de B, lo ve y lo invita
(quickstart §2).

### Tests for User Story 1

- [ ] T021 [P] [US1] [Bernardo] Prueba primero: `AS-test/application/service/SearchNormalizerTest.java`: "José Pérez" → `["jose", "perez"]`; `@Caro.MZ` → `caro.mz`; espacios repetidos y vacíos fuera; texto con `.*`, comillas y emojis se conserva literal para escaparlo después
- [ ] T022 [P] [US1] [Bernardo] Prueba primero: `AS-test/infrastructure/adapter/outbound/persistence/UserSearchIntegrationTest.java` con Testcontainers: "jose" encuentra "José Pérez"; orden `username` exacto → prefijo de `username` → prefijo de nombre y, a igual rango, `username` ascendente; excluye a quien busca y a `settings.privateProfile = true`; nunca coincide por correo; `.*` devuelve vacío; dos páginas de 20 sin repetidos ni faltantes
- [ ] T023 [P] [US1] [Bernardo] Prueba primero: `AS-test/infrastructure/migration/Ola2aSearchMigrationIntegrationTest.java`: usuarios sin `search.*` quedan con los campos llenos, la migración se registra en `migrations` y una segunda ejecución no cambia nada
- [ ] T024 [P] [US1] [Bernardo] Prueba de controlador contra los fixtures: `AS-test/infrastructure/adapter/inbound/rest/UserControllerContractTest.java` para `searchUsers` (200, vacío, 400 con `query` de 1 y de 51 caracteres y con `size` de 21, 401), comprobando que ninguna respuesta tiene `email` ni `phone`
- [ ] T025 [P] [US1] [Bernardo] Prueba del gateway: `GW/src/test/java/com/vito/gateway/controller/UsersSearchRateLimitIntegrationTest.java`: 15 búsquedas seguidas del mismo usuario → `429 RATE_LIMITED` desde la 11.ª, con `Retry-After: 1` y envelope; otro usuario no se ve afectado

### Implementation for User Story 1

- [ ] T026 [US1] [Bernardo] `AS/application/service/SearchNormalizer.java` (minúsculas, NFD sin marcas diacríticas, palabras sin vacíos, quita una `@` inicial). T021 pasa
- [ ] T027 [US1] [Bernardo] `AS/domain/model/User.java`: campos `search.username` y `search.terms`; llenarlos en el registro en `AS/application/service/AuthServiceImpl.java` y en cualquier cambio de `username` o `displayName`
- [ ] T028 [US1] [Bernardo] `AS/infrastructure/migration/Ola2aSearchMigration.java` (patrón `Ola1DataMigration`) que llena `search.*` en todos los usuarios, y después los índices `{"search.username": 1}` y `{"search.terms": 1}` en `AS/infrastructure/adapter/outbound/persistence/UserMongoAdapter.java`. T023 pasa
- [ ] T029 [US1] [Bernardo] `UserRepository.search(query, excludeUserId, page, size)` en `AS/domain/port/outbound/UserRepository.java` e implementación en `UserMongoAdapter.java`: agregación con prefijo anclado y **escapado** sobre `search.username` y `search.terms`, exclusión de quien busca y de privados, rango 0/1/2 y orden `rango, search.username`, `$skip`/`$limit` en la base (research R3). T022 pasa
- [ ] T030 [US1] [Bernardo] Puerto de entrada `AS/domain/port/inbound/UserDirectoryService.java` y su servicio en `AS/application/service/UserDirectoryServiceImpl.java` con las validaciones de `data-model.md` ("`query` de búsqueda con 2 a 50 caracteres (tras quitar la @ inicial)", "`size` de la búsqueda de 1 a 20") y la vista `UserSummary`
- [ ] T031 [US1] [Bernardo] `AS/infrastructure/adapter/inbound/rest/UserController.java` (`GET /api/v1/users/search`, quien busca desde `X-User-Id`) y `/api/v1/users/**` autenticado en `AS/infrastructure/config/SecurityConfig.java`. T024 pasa
- [ ] T032 [US1] [Bernardo] Gateway en `GW/src/main/resources/application.yml`: ruta `users-search` (`Path=/api/v1/users/search`, `Method=GET`) → `auth-service` con `RequestRateLimiter` por usuario (`replenishRate: 1`, `burstCapacity: 10`) y ruta `users` (`Path=/api/v1/users/**`) con el límite de `auth` (10/20), las dos con el CircuitBreaker de `auth`. T025 pasa
- [ ] T033 [US1] [Bernardo] Retirar `GET /api/v1/social/search` de `SS/infrastructure/adapter/inbound/rest/SocialController.java` y `searchUsers` del puerto y el servicio de `social-service`, con sus pruebas
- [ ] T034 [P] [US1] [Alex] Datasource y caso de uso de búsqueda: `GET /users/search` en un datasource de usuarios (`APP/features/social/data/datasources/user_remote_datasource.dart`) que devuelve `UserSummaryModel`; quitar `/social/search` de `social_remote_datasource.dart`, del repositorio y de `social_bloc.dart`. `429` se muestra como "Espera un momento y vuelve a intentarlo"
- [ ] T035 [US1] [Alex] `APP/features/events/presentation/pages/create_event_screen.dart`: "¿A quién invitamos?" usa la búsqueda nueva desde 2 caracteres (con espera de 300 ms entre teclas), quita el aviso provisional de Bug-F08 y, al crear el evento, llama a `sendInvitation` por cada persona elegida. Prueba de widget en `APP-test/features/events/`

**Checkpoint**: US1 se puede liberar sola (cierra Bug-F08).

---

## Phase 4: User Story 2 - Seguir y dejar de seguir a alguien (Priority: P1)

**Goal**: follow y unfollow atómicos e idempotentes.

**Independent Test**: A sigue a B dos veces, el contador sube una; A deja de seguir dos veces, vuelve
a 0 (quickstart §3).

### Tests for User Story 2

- [ ] T036 [P] [US2] [Bernardo] Prueba primero: `SS-test/infrastructure/adapter/outbound/persistence/FollowConcurrencyIntegrationTest.java` con Testcontainers: 50 hilos siguen el mismo par → una relación; 1 000 follow y unfollow mezclados sobre los mismos pares → los contadores (`count`) coinciden con las relaciones y ninguno es negativo (SC-004). Debe fallar con el `exists` + `save` actual
- [ ] T037 [P] [US2] [Bernardo] Prueba primero: `SS-test/application/service/SocialServiceImplTest.java` (reescribir): seguirse a sí mismo → `400`; destino inexistente según `UserDirectory` → `404`; repetir follow o unfollow → sin error y sin evento publicado; un cambio real → un evento
- [ ] T038 [P] [US2] [Bernardo] Prueba de controlador contra los fixtures `followUser.*` y `unfollowUser.*`: `SS-test/infrastructure/adapter/inbound/rest/SocialControllerContractTest.java` (`204` sin cuerpo, `400`, `404`, `503`)

### Implementation for User Story 2

- [ ] T039 [US2] [Bernardo] `FollowRepository` en `SS/domain/port/outbound/FollowRepository.java` y `FollowMongoAdapter.java`: `boolean insertIfAbsent(relación)` (insert; `DuplicateKeyException` → `false`) y `boolean deleteIfPresent(followerId, followingId)`; retirar `existsByFollowerIdAndFollowingId` + `save` del flujo. T036 pasa
- [ ] T040 [US2] [Bernardo] `follow` y `unfollow` en `SS/application/service/SocialServiceImpl.java` según research R5 (validar uno mismo → `400 VALIDATION_ERROR`; destino con `UserDirectory.findByIds([id])` → `404 NOT_FOUND` si no vuelve; publicar solo si la relación cambió), sin `$inc` sobre `user_profiles`. T037 pasa
- [ ] T041 [US2] [Bernardo] `SocialController.java`: follow y unfollow responden `204` sin cuerpo. T038 pasa
- [ ] T042 [P] [US2] [Alex] `APP/features/social/data/datasources/social_remote_datasource.dart` y `APP/features/social/presentation/widgets/follow_button.dart`: `204` como éxito, actualización optimista del botón y del contador con reversión si falla

---

## Phase 5: User Story 3 - Ver el perfil social de una persona (Priority: P2)

**Goal**: perfil con identidad real, contadores y relación, sin `user_profiles`.

**Independent Test**: A abre el perfil de B (anterior a la ola) y ve sus datos reales, contadores e
indicadores; en su propio perfil, los indicadores en `false` (quickstart §4).

### Tests for User Story 3

- [ ] T043 [P] [US3] [Bernardo] Prueba primero: `SS-test/infrastructure/adapter/inbound/rest/SocialProfileSerializationTest.java`: el JSON trae `isFollowing` e `isFollowedBy` (no `following` ni `followedBy`, T-B13) y coincide con `getSocialProfile.200.json` y `getSocialProfile.own.200.json`
- [ ] T044 [P] [US3] [Bernardo] Prueba de controlador contra `getSocialProfile.*` en `SocialControllerContractTest.java`: `200`, propio perfil, `404` (usuario que `auth-service` no devuelve) y `503` (con `auth-service` simulado caído)

### Implementation for User Story 3

- [ ] T045 [US3] [Bernardo] `getSocialProfile` en `SocialServiceImpl.java`: identidad con `UserDirectory` (un id), `followersCount = count({followingId})`, `followingCount = count({followerId})`, `isFollowing`/`isFollowedBy` con dos consultas de existencia y `false` en el propio perfil (research R6 y `data-model.md`); `SS/domain/model/SocialProfile.java` sin métodos `increment*`/`decrement*`
- [ ] T046 [US3] [Bernardo] DTO `SS/infrastructure/adapter/inbound/rest/dto/SocialProfileResponse.java` (`record` o `@JsonProperty("isFollowing")`/`@JsonProperty("isFollowedBy")`) y `SocialController.java` responde el DTO, no el modelo de dominio. T043 y T044 pasan
- [ ] T047 [US3] [Bernardo] Retirar `GET /api/v1/social/is-following/{userId}` del controlador y del puerto `SocialService`, y el puerto `UserProfileRepository` con `UserProfileMongoAdapter.java`. La colección `user_profiles` no se borra (Principio IX)
- [ ] T048 [P] [US3] [Alex] `APP/features/social/presentation/pages/profile_page.dart` y `stats_card.dart` con `getSocialProfile`: nombre, usuario, avatar, contadores, "te sigue", sin botón de seguir en el propio perfil; quitar la llamada a `/social/is-following` del datasource, el repositorio y el bloc; `503` con "No pudimos cargar el perfil, intenta de nuevo"

---

## Phase 6: User Story 4 - Ver seguidores y seguidos (Priority: P3)

**Goal**: listas paginadas en la base con identidad real.

**Independent Test**: 25 seguidores → página 0 con 20 y página 1 con 5; 500 seguidores recorridos de
50 en 50 sin repetidos (quickstart §5).

### Tests for User Story 4

- [ ] T049 [P] [US4] [Bernardo] Prueba primero: `SS-test/infrastructure/adapter/outbound/persistence/FollowPaginationIntegrationTest.java` con Testcontainers: 500 seguidores con varios `createdAt` iguales → recorrer con `size=50` devuelve cada uno exactamente una vez, del más reciente al más antiguo (SC-005); página más allá del final → vacía
- [ ] T050 [P] [US4] [Bernardo] Prueba de controlador contra `listFollowers.*` y `listFollowing.*`: `200`, vacío, `400` con `size` de 51 y `page` negativa, `404`, y un id que `UserDirectory` no devuelve se omite de la página

### Implementation for User Story 4

- [ ] T051 [US4] [Bernardo] `FollowMongoAdapter.java`: `findFollowerIds(userId, page, size)` y `findFollowingIds(userId, page, size)` con orden `createdAt` y `_id` descendentes y `skip`/`limit` en la base; retirar `findFollowerIdsByFollowingId`/`findFollowingIdsByFollowerId` (traían todo). T049 pasa
- [ ] T052 [US4] [Bernardo] `getFollowers`/`getFollowing` en `SocialServiceImpl.java`: comprobar el usuario con `UserDirectory` (`404`), una sola llamada `findByIds` por página y orden de las relaciones; validación "`size` de las listas de 1 a 50; `page` ≥ 0" en `SocialController.java`. T050 pasa
- [ ] T053 [P] [US4] [Alex] Listas de seguidores y seguidos desde el perfil (`APP/features/social/presentation/pages/`), con paginación infinita que pide hasta recibir una página vacía (una página puede traer menos de `size`)
- [ ] T054 [US4] [Alex] `APP/features/home/presentation/pages/people_screen.dart` ("Mi Gente"): a quién sigues y quién te sigue con `listFollowing`/`listFollowers` del usuario con sesión, búsqueda con `searchUsers` y estados vacíos ("Aún no sigues a nadie"); sin datos falsos (parte social de T-F02)

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T055 [P] [Bernardo] Prueba de privacidad (SC-006): `SS-test/PrivacyFixturesTest.java` y `AS-test/PrivacyFixturesTest.java` recorren `FX/users/` y `FX/social/` y fallan si aparece `email`, `phone` o un texto con forma de correo
- [ ] T056 [P] [Bernardo] Escenario k6 `backend/load-tests/ola-2a-social.js`: búsqueda (< 1 s p95, SC-003) y perfil y listas (< 300 ms p95), siguiendo `backend/load-tests/ola-1-rsvp.js`. En local vale para corrección; el p95 se mide en un entorno representativo
- [ ] T057 [P] [Bernardo] Tablero del gateway de T-I13 (`infrastructure/observability/`): `429` de `users-search` y latencia y errores del `RestClient` de `social-service` hacia `auth-service`
- [ ] T058 [Bernardo] Registrar en `docs/roadmap/tasks.md` la desviación del Principio V de `social-events` (sobre versionado, outbox y `asyncapi.yaml`) junto a T-B36, con dependencia de T-C05 y antes del primer consumidor
- [ ] T059 [Bernardo] y [Alex] Recorrer `quickstart.md` completo contra el stack local y pegar la salida en el PR de cierre de la ola
- [ ] T060 [Alex] Cerrar en `docs/roadmap/tasks.md` T-C07 Ola 2a, Bug-F08 (VITO-83, tras quickstart §2.9 en producción), la parte social de T-F02 y la parte social de T-B13; cerrar el RFC #277

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 ✅ (D-08 aprobada con el merge de #283, 2026-11-10). T003 y T004 ✅ en #284 (2026-11-10). T004 no tocó `.env.example` ni los fixtures de Prod Startup: la variable tiene default y en Compose va fija por el DNS interno, como `EVENT_SERVICE_URI` de `mosaic-service`.
- **Foundational (Phase 2)**: T005–T010 forman un solo PR de contrato y bloquean todo lo demás.
  T011–T012 (app) y T013–T020 (backend) empiezan cuando ese PR se mergea y avanzan en paralelo.
  T014 (migración) va antes de T015 (índice único) y se despliega con él.
- **User Stories (Phase 3+)**: dependen de la Phase 2. US1 es independiente de las demás y solo
  toca `auth-service` y el gateway; US2, US3 y US4 usan `UserDirectory` (T016–T018), que a su vez
  necesita `getUsersByIds` (T061–T062, en `auth-service`).
- **Polish**: al final.

### User Story Dependencies

- **US1 (P1)**: tras la Phase 2. Independiente; se puede liberar sola.
- **US2 (P1)**: tras la Phase 2 y T061–T062.
- **US3 (P2)**: tras la Phase 2 y T061–T062. Independiente de US2 (el perfil se ve aunque nadie siga a nadie).
- **US4 (P3)**: tras la Phase 2 y T061–T062. La pantalla "Mi Gente" (T054) usa también la búsqueda de US1.

### Within Each User Story

- Pruebas primero; en backend, deben fallar antes de implementar.
- Backend: dominio → puerto/adaptador → servicio → controlador → gateway.
- App: modelos (T011) → datasource y casos de uso → bloc → pantalla, contra el mock de Prism
  (`npm run mock:api`) sin esperar al backend.

### Parallel Opportunities

- Backend (Bernardo) y app (Alex) avanzan en paralelo en todas las historias tras el contrato.
- Dentro de la Phase 2: T013, T016 y T020 tocan archivos distintos; T017 y T018 van juntas.
- `auth-service` (US1, T061–T062) y `social-service` (T013–T020) se pueden trabajar en paralelo; la
  integración de US2–US4 espera a T062.
- Las pruebas marcadas [P] de cada historia van en paralelo.

---

## Parallel Example: User Story 1

```text
# Bernardo, en paralelo:
T021 SearchNormalizerTest · T022 UserSearchIntegrationTest · T023 Ola2aSearchMigrationIT · T024 UserControllerContractTest · T025 UsersSearchRateLimitIT

# Alex, en paralelo con todo lo anterior (contra el mock):
T034 datasource de búsqueda → T035 "¿A quién invitamos?"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 + contrato (T005–T010) mergeado con D-08 de la Ola 2a.
2. US1 completa (T021–T035): búsqueda en `auth-service`, gateway y pantalla de invitar.
3. **Validar** con quickstart §2 y liberar: cierra Bug-F08 sin esperar al grafo social.

### Incremental Delivery

1. MVP (US1).
2. Base de social (T013–T020) + `getUsersByIds` (T061–T062) + US2 (seguir): hito 2 del MVP.
3. US3 (perfil) + US4 (listas y "Mi Gente"): cierra la parte social de T-F02 y de T-B13.
4. Polish: privacidad, k6, tablero y quickstart completo.

### Mapeo a `docs/roadmap/tasks.md`

Se registran en `docs/roadmap/tasks.md` con este PR, con su fila en la tabla resumen y la
"Asignación del 2026-11-09 (Ola 2a)":

| Backlog | Dueño | Tareas de esta feature | Estado inicial |
|---------|-------|------------------------|----------------|
| T-C11 · Contrato de la Ola 2a | Agente de Alex | T002, T005–T010, T060 | 🟡 RFC (requiere T001) |
| T-F13 · App de la Ola 2a | Agente de Alex | T011–T012, T034–T035, T042, T048, T053–T054 | 🔴 BLOQUEADA por T-C11 |
| T-B38 · `auth-service` y gateway de la Ola 2a (búsqueda y datos por lotes) | Bernardo | T021–T033, T061–T062 | 🔴 BLOQUEADA por T-C11 |
| T-B39 · `social-service` de la Ola 2a (grafo social) | Bernardo | T003, T013–T020, T036–T041, T043–T047, T049–T052, T055, T058 | 🔴 BLOQUEADA por T-C11 (T003 ✅ #284; T013–T015 y T020 adelantadas en #286, borrador) |
| T-I30 · `AUTH_SERVICE_URI`, carga y tablero de la Ola 2a | Bernardo | T004, T056–T057 | 🔴 BLOQUEADA por T-B39 (T004 ✅ #284) |

T001 (D-08) y T059 son compartidas por los dos leads.

---

## Notes

- Cada bloque del mapeo es un PR a `develop`, sin apilar (CONTRIBUTING §2); el de contrato primero.
- Coordinación con #282 (T-B18): si se mergea antes, `GlobalExceptionHandler` y el filtro JWT de
  `auth-service` y `social-service` vienen de `backend/shared-web`; las tareas T019 y T031 se
  ajustan a esa librería.
- Docker apagado = pruebas de Testcontainers saltadas: revisar `skipped="0"` en los reportes.
- Commit tras cada tarea o grupo lógico; marcar `[X]` aquí y el estado en `tasks.md` del roadmap en
  el mismo PR.
