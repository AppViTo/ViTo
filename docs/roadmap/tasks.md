# Backlog — Alineación contrato ↔ implementación

> Backlog único. **Un agente solo toma tareas 🟢 `READY`.** Antes de tomar una, leer
> [`../handoff/AGENT-BRIEF.md`](../handoff/AGENT-BRIEF.md).
>
> Al tomar una tarea: cambiar su estado a `🔵 EN CURSO (<rama>)` en el mismo PR.
> Al cerrar: `✅ HECHA (#PR)`. Si al implementar descubres que la tarea toca el contrato
> o una decisión pendiente, **detente**, márcala `⛔ BLOQUEADA (motivo)` y avisa.

## Estados

| Estado | Significado |
|--------|-------------|
| 🟢 `READY` | Se puede implementar y mergear ya. No cambia el contrato. |
| 🟡 `RFC` | Se puede **redactar** ya, pero toca `docs/api/**` o `docs/decisions/**`: requiere issue `contract-change` y aprobación de 1 CODEOWNER antes de mergear. |
| 🔴 `BLOQUEADA` | Espera una decisión (D-xx en `decisions-pending.md`) u otra tarea (T-xx). |
| 🔵 `EN CURSO` | Alguien la tomó (indicar rama). |
| ✅ `HECHA` | Mergeada con su criterio de cierre verificado. |

## Pistas y dueño sugerido

| Pista | Prefijo | Dueño sugerido | Rama |
|-------|---------|----------------|------|
| Contrato y tooling | `T-C` | Agente frontend (dueño de `docs/api/**`) | `contract/<nombre>` o `docs/<nombre>` |
| Frontend Flutter | `T-F` | Agente frontend | `frontend/<nombre>` |
| Backend Java/Python | `T-B` | Agente backend | `backend/<nombre>` |
| Infra, CI/CD, higiene | `T-I` | DevOps / cualquiera | `docs/<nombre>`, `backend/<nombre>` o `chore` |

---

## Resumen: qué se puede hacer desde ya

| ID | Tarea | Pista | Prioridad | Estado |
|----|-------|-------|-----------|--------|
| T-C01 | Fixtures de las 15 operaciones existentes | C | P0 | ✅ HECHA (#21) |
| T-C02 | `package.json` + mock REST con Prism | C | P0 | 🟢 READY (tras T-C01 para ejemplos ricos) |
| T-C03 | Job de CI "contract": Spectral + validación de fixtures | C | P0 | 🟢 READY |
| T-C04 | Tests Dart de serialización contra fixtures | C/F | P0 | 🟢 READY (tras T-C01) |
| T-C05 | `asyncapi.yaml` v0 con las 4 rutas WS de CONVENTIONS §7 | C | P0 | 🟡 RFC |
| T-F01 | AuthBloc real: login/register/logout/refresh sin mocks | F | P0 | 🟢 READY |
| T-F03 | Proveer `CheckInBloc` en raíz y ruta de `ArchiveDetailPage` | F | P1 | 🟢 READY |
| T-F04 | WS clients envían `?token=` (checkin, mosaic, streaming) | F | P0 | 🟢 READY |
| T-F06 | Build web falla si faltan `API_BASE_URL`/`WS_BASE_URL` | F | P1 | 🟢 READY |
| T-F07 | Rellenar tests vacíos y subir cobertura de auth | F | P1 | 🟢 READY |
| T-B01 | Gateway: borrar `X-User-*` entrantes en todas las rutas + rate limit por IP en públicas | B | P0 | ✅ HECHA (#19) |
| T-B02 | Gateway: 401/fallbacks con envelope ADR-003 | B | P1 | 🟢 READY |
| T-B04 | Gateway: ruta `/ws/**` con validación de `?token=` y cierre 4401 | B | P0 | ✅ HECHA (#22, re-entrega de #20) |
| T-B05 | `WebSocketConfig` WS crudo en mosaic y streaming | B | P0 | ✅ HECHA (#25) |
| T-B08 | `hideAddressUntilAccepted` en event-service | B | P1 | 🟢 READY |
| T-B09 | Agregar servicios FastAPI a `docker-compose.yml` | B/I | P1 | 🟢 READY |
| T-B12 | event-service: `settings.isPublic` se serializa como `"public"` y se ignora al recibir | B | P0 | 🟢 READY |
| T-B11 | Gateway: rate limiter y circuit breaker inactivos en perfiles `dev`/`docker` | B | P0 | ✅ HECHA (#24) |
| T-I01 | Higiene: `.gitignore` `backend/out/`, borrar `backend/java/` huérfano | I | P2 | 🟢 READY |
| T-I02 | `integration-tests/`: darle `pom.xml` o retirarlo | I | P2 | 🟢 READY |
| T-I03 | `docs/architecture.md` alineado a ADR-001 y al inventario real | I | P2 | 🟢 READY |

Bloqueadas (no tomar): T-C06, T-C07, T-F02, T-F05, T-F08, T-B03, T-B06, T-B07, T-B10, T-I04, T-I05.

---

## Pista C — Contrato y tooling

### T-C01 · Fixtures de las 15 operaciones existentes — ✅ HECHA (#21) · P0 · O-01
- **Qué:** crear `docs/api/fixtures/<tag>/<operationId>.<status>.json` (request y
  response) para cada operación de `openapi.yaml`, incluidos los errores 400/401/404/409
  declarados. Añadir `docs/api/fixtures/README.md` con la convención de nombres.
- **Fuente:** los `examples` de `openapi.yaml` + `CONVENTIONS.md` §3–§6 + ADR-001/002/003.
- **No hacer:** cambiar schemas del YAML. Si un ejemplo contradice al schema, se corrige el
  ejemplo; si el schema parece mal, abrir RFC.
- **Cierre:** cada `operationId` tiene al menos un fixture 2xx y uno de error; T-C03
  los valida contra el schema.

### T-C02 · `package.json` + mock REST (Prism) — 🟢 READY · P0 · O-04
- **Qué:** `package.json` en la raíz con `@stoplight/prism-cli`, `@stoplight/spectral-cli`,
  `@asyncapi/cli` fijados y scripts `lint:api`, `lint:async`, `mock:api` (Prism en :4010).
  `mock:ws` queda como script que falla con mensaje "pendiente de T-C05".
- **Cierre:** `npm ci && npm run mock:api` sirve las 15 operaciones; Flutter corre contra
  `http://localhost:4010/api/v1` (comando ya documentado en CONTRIBUTING §6).

### T-C03 · Job de CI "contract" — 🟢 READY · P0 · Principio I
- **Qué:** job en `.github/workflows/ci.yml` que corre `npm run lint:api` y un script que
  valida cada fixture contra su schema (p. ej. `ajv` sobre los schemas de OpenAPI).
  `lint:async` se activa cuando exista `asyncapi.yaml`.
- **Cierre:** el job es requerido en los PR que tocan `docs/api/**` y está verde.

### T-C04 · Tests Dart contra fixtures — 🟢 READY (tras T-C01) · P0 · Principio IV
- **Qué:** en `frontend/vito_app/test/contract/`, un test por fixture 2xx: carga el JSON
  de `docs/api/fixtures/`, hace `Model.fromJson` y `toJson` y compara campos.
  Modelos: `AuthResponse`/`User`, `EventModel`, chat (`ChatRoom`, `ChatMessage`), `Media`.
- **Cierre:** `flutter test test/contract` verde; un cambio de fixture rompe el test.

### T-C05 · `asyncapi.yaml` v0 — 🟡 RFC · P0 · O-01, ADR-006
- **Qué:** documentar las rutas WS que **ya** fija `CONVENTIONS.md` §7 (checkin, mosaic,
  streaming, chat) con el formato de frame `{type, data}`. Los `type` se toman de los
  handlers existentes (`*WebSocketHandler.java`) y de los repositorios Flutter que los
  consumen; donde difieran, listar la diferencia en el RFC. Mensajes Kafka se agregan por
  ola (no en v0). Fixtures de frames en `docs/api/fixtures/ws/`.
- **Cierre:** `npx @asyncapi/cli validate docs/api/asyncapi.yaml` verde; PR aprobado por
  1 CODEOWNER; `mock:ws` de T-C02 sirve los frames.

### T-C06 · Resolver `GET /events` (O-26) — 🔴 BLOQUEADA por D-03 · P0
- **Qué:** según la opción elegida en D-03, PR `contract/events-listing` + fixture.
- **Desbloquea:** T-B07, T-F08.

### T-C07 · Ramas `contract/ola-N` (1 a 6) — 🔴 BLOQUEADA por D-01 + D-08 (por ola) · O-02, O-30
- **Qué:** por cada ola aprobada en `endpoint-inventory.md`: paths + schemas en
  `openapi.yaml`, frames/eventos en `asyncapi.yaml`, fixtures, ADR si aplica y handoff
  `docs/handoff/<ola>.md` desde `TEMPLATE.md`. Se ejecuta con Spec Kit
  (`/speckit-specify` referenciando la ola y las O-xx que cierra).

---

## Pista F — Frontend Flutter

### T-F01 · AuthBloc real — 🟢 READY · P0 · O-20
- **Qué:** quitar el `// MOCK` de `auth_bloc.dart` (`_onLoginRequested`, `_onAuthCheckRequested`)
  y usar `LoginUseCase`/`RegisterUseCase`/`LogoutUseCase` + persistencia de tokens y
  refresh automático en el interceptor Dio (ADR-004). Solo operaciones del contrato:
  `register`, `login`, `refresh`, `logout`.
- **Auto-login:** con lo que hay en contrato, se resuelve con el refresh token guardado
  (`POST /auth/refresh`) y el `user` del `AuthResponse`. `GET /auth/me` **no** se usa
  hasta la Ola 1.
- **Cierre:** sin datos hardcodeados en `features/auth`; tests de bloc para éxito, 401,
  400 y refresh; funciona contra `npm run mock:api`.

### T-F02 · Retirar `MockApiService` (home, people) — 🔴 BLOQUEADA por T-C07 (Olas 1–2)
- Home necesita eventos (`GET /events` → D-03) y people necesita `social/search` (Ola 2).

### T-F03 · Wiring de blocs y rutas — 🟢 READY · P1
- **Qué:** proveer `CheckInBloc` donde se usan sus páginas (o en el `MultiBlocProvider`
  raíz) y registrar la ruta de `ArchiveDetailPage` en `config/routes/app_router.dart`.
- **No hacer:** conectar endpoints nuevos.
- **Cierre:** navegar a check-in y a detalle de archivo no lanza `ProviderNotFound`;
  widget test de cada ruta.

### T-F04 · `?token=` en los WebSocket — 🟢 READY · P0 · CONVENTIONS §7, O-28
- **Qué:** `checkin_repository_impl.dart`, `mosaic_repository_impl.dart`,
  `streaming_repository_impl.dart` construyen la URL desde `Env.wsBaseUrl` (no
  `baseUrl.replaceFirst('http','ws')`) y agregan `?token=<accessToken>`. Manejar cierre
  `4401` → refresh + reconexión una vez.
- **Chat:** no tocar `?userId=` hasta D-06.
- **Cierre:** test unitario que verifica la URL generada y el manejo de 4401.

### T-F05 · Subida al mosaico — 🔴 BLOQUEADA por D-01 + Ola 3
### T-F08 · Lista "mis eventos" — 🔴 BLOQUEADA por D-03 (T-C06)

### T-F06 · Build web estricto — 🟢 READY · P1 · O-15
- **Qué:** el build de producción (`deploy-frontend.yml` y/o un check en `Env`) falla si
  `API_BASE_URL` o `WS_BASE_URL` están vacíos; el default `localhost` solo en debug.
- **Cierre:** el job de Vercel falla sin las variables; pasa con ellas.

### T-F07 · Tests vacíos y cobertura — 🟢 READY · P1 · O-19
- **Qué:** `test/widget_test.dart` y `test/features/events/create_event_screen_test.dart`
  tienen 0 casos: rellenarlos o eliminarlos. Añadir `flutter test --coverage` al CI.
- **Cierre:** no hay archivos de test sin casos; reporte de cobertura publicado como
  artefacto.

---

## Pista B — Backend

### T-B01 · Gateway: higiene de `X-User-*` y rate limit — ✅ HECHA (#19) · P0 · O-05, O-27, ADR-007
- **Qué:** en `JwtAuthenticationFilter` (o un `GlobalFilter` previo) eliminar
  `X-User-Id`, `X-User-Email`, `X-User-Roles` de **toda** petición entrante, también en
  rutas públicas, antes de inyectar los propios. `RateLimiterConfig` usa el usuario
  autenticado solo si lo puso el gateway; en rutas públicas usa la IP del cliente.
- **Cierre:** tests: (1) petición pública con `X-User-Id: x` llega al servicio sin ese
  header; (2) dos logins con distintos `X-User-Id` falsos comparten bucket de rate limit.

### T-B02 · Gateway: envelope ADR-003 — 🟢 READY · P1 · O-10
- **Qué:** el 401 del filtro y `FallbackController` devuelven
  `{error, message, status, timestamp}` (`UNAUTHORIZED`, `SERVICE_UNAVAILABLE`).
- **Cierre:** test de cada respuesta comparada con el schema `ErrorResponse`.

### T-B03 · ADR-003 en los 6 servicios sin handler — 🔴 BLOQUEADA por D-05 · P1 · O-29
- Servicios sin `@RestControllerAdvice`: social, event, checkin, mosaic, streaming, archive.
- Si D-05 = opción C, pasa a READY copiando el handler de `auth-service`.

### T-B04 · Gateway: ruta `/ws/**` — ✅ HECHA (#22, re-entrega de #20) · P0 · O-08, O-28, ADR-006
- **Qué:** rutas `ws://` en el gateway para `/ws/checkin/**`, `/ws/mosaic/**`,
  `/ws/streaming/**`, `/ws/chat/**` hacia cada servicio; validar `?token=` con la misma
  lógica JWT, inyectar `X-User-*` y cerrar con `4401` si es inválido.
- **Cierre:** test de handshake válido / inválido; el frontend conecta vía :8080.

### T-B05 · WebSocketConfig en mosaic y streaming — ✅ HECHA (#25) · P0 · ADR-006, O-28
- **Qué:** registrar `MosaicWebSocketHandler` en `/ws/mosaic/{eventId}` y
  `StreamingWebSocketHandler` en `/ws/streaming/{streamId}` como WS crudo (igual que
  checkin). Sin cambiar los `type` de frame existentes (se documentan en T-C05).
- **Cierre:** test de integración que conecta y recibe un frame.

### T-B06 · Chat a WS crudo — 🔴 BLOQUEADA por T-C05 + D-06 · P1 · ADR-006, O-28
- Reemplazar STOMP/SockJS de `chat-service` por handler crudo con los frames de
  `asyncapi.yaml`.

### T-B07 · Implementar listado de eventos — 🔴 BLOQUEADA por D-03 (T-C06) · P0 · O-26

### T-B08 · `hideAddressUntilAccepted` — 🟢 READY · P1 · ADR-001, Principio IX, O-33
- **Qué:** el campo existe en contrato, ADR-001 y Flutter, pero no en
  `Event.EventSettings` de event-service. Agregarlo, persistirlo y, en `GET /events/{id}`,
  omitir `details.address`/`latitude`/`longitude` si es `true` y quien consulta no es
  el organizador ni tiene RSVP `ACCEPTED`.
- **Cierre:** tests de dominio para organizador / aceptado / no aceptado; respuesta igual
  al fixture `getEvent` de T-C01.

### T-B09 · FastAPI en compose — 🟢 READY · P1 · O-31
- **Qué:** agregar transcode, moderation y recommendation al profile `app` de
  `docker-compose.yml` en los puertos que espera el gateway (8000/8001/8002), con
  healthcheck.
- **Cierre:** `docker compose --profile app up` levanta los 3 y el gateway los alcanza.

### T-B11 · Rate limiter y circuit breaker activos en todos los perfiles — ✅ HECHA (#24) · P0 · O-14, O-27
- **Hallazgo (T-B01):** `application-dev.yml` y `application-docker.yml` redefinen
  `spring.cloud.gateway.routes` completas sin filtros. Spring reemplaza la lista entera, así
  que `RequestRateLimiter` y `CircuitBreaker` de `application.yml` **no se aplican** en
  ningún perfil real (`spring.profiles.active: dev` por defecto; compose usa `docker`).
  Verificado en el log de arranque: cada ruta solo aplica `DedupeResponseHeader`.
- **Qué:** una sola definición de rutas en `application.yml` con URIs parametrizadas
  (`${AUTH_SERVICE_URI:http://localhost:8081}`, …) y los perfiles solo fijan esas variables
  (compose las pasa por entorno). Cierra también los `localhost` del perfil base (O-14).
- **Cierre:** test de contexto que verifica que la ruta `auth-service` tiene
  `RequestRateLimiter` y `CircuitBreaker` con los perfiles `dev` y `docker`.

### T-B10 · Spring Boot y Java unificados — 🔴 BLOQUEADA: requiere spec propio (Principio X) · P1 · O-21, O-35
- 10 poms en 3.2.1, 2 en 3.5.3, Java 17. Planificar con `/speckit-specify`.

### T-B12 · `isPublic` con el nombre del contrato — 🟢 READY · P0 · Principio I, ADR-001
- **Hallazgo (T-B08):** `Event.EventSettings` declara `boolean isPublic` con Lombok, que
  genera `isPublic()`/`setPublic()`; Jackson lo expone como **`"public"`**. El contrato
  (ADR-001, CONVENTIONS §6) y Flutter usan `"isPublic"`. Al recibir, Spring Boot ignora la
  propiedad desconocida: **todo evento creado desde la app queda `isPublic=false`** y las
  respuestas traen `"public"`. Verificado con un test de serialización.
- **Qué:** que REST lea y escriba `isPublic` sin acoplar el dominio a Jackson (DTO/mapper
  en `infrastructure`, o mixin de Jackson registrado en config). Revisar el mismo patrón
  `boolean is*` en otros servicios.
- **Cierre:** test que serializa y deserializa `{"settings":{"isPublic":true}}` y fixture
  `createEvent` de T-C01 aceptado tal cual.

---

## Pista I — Infra, CI/CD e higiene

### T-I01 · Higiene del repo — 🟢 READY · P2 · O-23, O-25, O-34
- **Qué:** agregar `backend/out/` al `.gitignore`; borrar `backend/java/` (copia huérfana
  de `com/vito/gateway`) tras confirmar que ningún pom/Dockerfile lo referencia.
  Listar en el PR qué hacer con `serve.js`, `Creación`, `img/` y el `.docx` de la raíz
  (mover a `docs/` o eliminar) y esperar OK del lead antes de borrarlos.
- **Cierre:** `git status` limpio tras un build. (La cola UTF-16 del README raíz ya se corrigió en el PR de la constitución v1.1.0.)

### T-I02 · `integration-tests/` — 🟢 READY · P2 · O-19
- **Qué:** hoy solo tiene `src/` sin `pom.xml`. Darle `pom.xml` con Testcontainers y un
  job de CI manual, o retirarlo. Proponer en el PR.

### T-I03 · `architecture.md` coherente — 🟢 READY · P2 · O-36
- **Qué:** el esquema `events` usa la forma plana (`name`, `date`, `isPublic`…) que
  ADR-001 eliminó; faltan checkin/mosaic/archive en los diagramas. Alinearlo con ADR-001,
  `endpoint-inventory.md` y marcar Dragonfly/Eureka/S3 como "en revisión (D-02)".

### T-I04 · CD por servicio — 🔴 BLOQUEADA por D-02 (registro y destino) · P0 · O-12, O-13
- `cd.yml` construye auth sin `file: backend/Dockerfile` y con el mismo nombre de imagen
  que transcode; los deploy son `echo`. Se rehace cuando se decida destino y registro.

### T-I05 · Kubernetes / despliegue — 🔴 BLOQUEADA por D-02 · P0 · O-13
