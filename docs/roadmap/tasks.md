# Backlog — Alineación contrato ↔ implementación

> Backlog único. **Un agente solo toma tareas 🟢 `READY`.** Antes de tomar una, leer
> [`../handoff/AGENT-BRIEF.md`](../handoff/AGENT-BRIEF.md).
>
> Al tomar una tarea: cambiar su estado a `🔵 EN CURSO (<rama>)` en el mismo PR.
> Al cerrar: `✅ HECHA (#44)`. Si al implementar descubres que la tarea toca el contrato
> o una decisión pendiente, **detente**, márcala `⛔ BLOQUEADA (motivo)` y avisa.

## Estados

| Estado | Significado |
|--------|-------------|
| 🟢 `READY` | Se puede implementar y mergear ya. No cambia el contrato. |
| 🟡 `RFC` | Se puede **redactar** ya, pero toca `docs/api/**` o `docs/decisions/**`: requiere issue `contract-change` y aprobación de 1 CODEOWNER antes de mergear. |
| 🔴 `BLOQUEADA` | Espera una decisión (D-xx en `decisions-pending.md`) u otra tarea (T-xx). |
| 🔵 `EN CURSO` | Alguien la tomó (indicar rama). |
| ✅ `HECHA` | Mergeada con su criterio de cierre verificado. |
| ⚪ `REEMPLAZADA` / `DIFERIDA` | Ya no aplica en la fase actual (se indica qué la reemplaza o cuándo vuelve). |

## Pistas y dueño sugerido

| Pista | Prefijo | Dueño sugerido | Rama |
|-------|---------|----------------|------|
| Contrato y tooling | `T-C` | Agente frontend (dueño de `docs/api/**`) | `contract/<nombre>` o `docs/<nombre>` |
| Frontend Flutter | `T-F` | Agente frontend | `frontend/<nombre>` |
| Backend Java/Python | `T-B` | Agente backend | `backend/<nombre>` |
| Infra, CI/CD, higiene | `T-I` | DevOps / cualquiera | `docs/<nombre>`, `backend/<nombre>` o `chore` |

---

## Resumen: qué se puede hacer desde ya

| ID | Tarea | Pista | Prioridad | Estado | Jira |
|----|-------|-------|-----------|--------|------|
| T-C01 | Fixtures de las 15 operaciones existentes | C | P0 | ✅ HECHA (#21) | [VITO-10](https://bernardo-mira.atlassian.net/browse/VITO-10) |
| T-C02 | `package.json` + mock REST con Prism | C | P0 | ✅ HECHA (#27) | [VITO-11](https://bernardo-mira.atlassian.net/browse/VITO-11) |
| T-C03 | Job de CI "contract": Spectral + validación de fixtures | C | P0 | ✅ HECHA (#44) | [VITO-12](https://bernardo-mira.atlassian.net/browse/VITO-12) |
| T-C04 | Tests Dart de serialización contra fixtures | C/F | P0 | ✅ HECHA (#31) | [VITO-13](https://bernardo-mira.atlassian.net/browse/VITO-13) |
| T-C05 | `asyncapi.yaml` v0 con las 4 rutas WS de CONVENTIONS §7 | C | P0 | 🟡 RFC | [VITO-14](https://bernardo-mira.atlassian.net/browse/VITO-14) |
| T-C06 | `listEvents` con `scope=mine\|upcoming` según ADR-014 | C | P0 | 🟡 RFC | [VITO-15](https://bernardo-mira.atlassian.net/browse/VITO-15) |
| T-C08 | Alinear `AuthResponse`, `User` de auth y `logoutUser` de openapi/fixtures/tests Dart con ADR-004 | C/F | P0 | ✅ HECHA (#45) | [VITO-17](https://bernardo-mira.atlassian.net/browse/VITO-17) |
| T-F01 | AuthBloc real: login/register/logout/refresh sin mocks | F | P0 | ✅ HECHA (#46, #56) | [VITO-18](https://bernardo-mira.atlassian.net/browse/VITO-18) |
| T-F03 | Proveer `CheckInBloc` en raíz y ruta de `ArchiveDetailPage` | F | P1 | 🟢 READY | [VITO-20](https://bernardo-mira.atlassian.net/browse/VITO-20) |
| T-F04 | WS clients envían `?token=` (checkin, mosaic, streaming, chat sin `?userId=`) | F | P0 | 🟢 READY | [VITO-21](https://bernardo-mira.atlassian.net/browse/VITO-21) |
| T-F06 | Build web falla si faltan `API_BASE_URL`/`WS_BASE_URL` | F | P1 | 🟢 READY | [VITO-23](https://bernardo-mira.atlassian.net/browse/VITO-23) |
| T-F07 | Rellenar tests vacíos y subir cobertura de auth | F | P1 | 🟢 READY | [VITO-24](https://bernardo-mira.atlassian.net/browse/VITO-24) |
| T-B01 | Gateway: borrar `X-User-*` entrantes en todas las rutas + rate limit por IP en públicas | B | P0 | ✅ HECHA (#19) | [VITO-26](https://bernardo-mira.atlassian.net/browse/VITO-26) |
| T-B02 | Gateway: 401/fallbacks con envelope ADR-003 | B | P1 | ✅ HECHA (#38) | [VITO-27](https://bernardo-mira.atlassian.net/browse/VITO-27) |
| T-B04 | Gateway: ruta `/ws/**` con validación de `?token=` y cierre 4401 | B | P0 | ✅ HECHA (#22, re-entrega de #20) | [VITO-29](https://bernardo-mira.atlassian.net/browse/VITO-29) |
| T-B05 | `WebSocketConfig` WS crudo en mosaic y streaming | B | P0 | ✅ HECHA (#25) | [VITO-30](https://bernardo-mira.atlassian.net/browse/VITO-30) |
| T-B08 | `hideAddressUntilAccepted` en event-service | B | P1 | ✅ HECHA (#26) | [VITO-33](https://bernardo-mira.atlassian.net/browse/VITO-33) |
| T-B09 | Agregar servicios FastAPI a `docker-compose.yml` | B/I | P1 | ✅ HECHA (#43) | [VITO-34](https://bernardo-mira.atlassian.net/browse/VITO-34) |
| T-B12 | event-service: `settings.isPublic` se serializa como `"public"` y se ignora al recibir | B | P0 | ✅ HECHA (#28) | [VITO-37](https://bernardo-mira.atlassian.net/browse/VITO-37) |
| T-B13 | `boolean is*` serializado sin `is` en archive, mosaic, social y streaming | B | P1 | 🔴 BLOQUEADA (olas 2–5) | [VITO-38](https://bernardo-mira.atlassian.net/browse/VITO-38) |
| T-B11 | Gateway: rate limiter y circuit breaker inactivos en perfiles `dev`/`docker` | B | P0 | ✅ HECHA (#24) | [VITO-36](https://bernardo-mira.atlassian.net/browse/VITO-36) |
| T-I01 | Higiene: `.gitignore` `backend/out/`, borrar `backend/java/` huérfano | I | P2 | 🟢 READY | [VITO-42](https://bernardo-mira.atlassian.net/browse/VITO-42) |
| T-I02 | `integration-tests/`: darle `pom.xml` o retirarlo | I | P2 | 🟢 READY | [VITO-43](https://bernardo-mira.atlassian.net/browse/VITO-43) |
| T-I03 | `docs/architecture.md` alineado a ADR-001 y al inventario real | I | P2 | 🟢 READY | [VITO-44](https://bernardo-mira.atlassian.net/browse/VITO-44) |
| T-B14 | Actuator + health + métricas Prometheus en todos los servicios | B | P0 | ✅ HECHA (#33) | [VITO-39](https://bernardo-mira.atlassian.net/browse/VITO-39) |
| T-B15 | Códigos y errores de auth-service y `createEvent` según los fixtures | B | P0 | ✅ HECHA (#39) | [VITO-40](https://bernardo-mira.atlassian.net/browse/VITO-40) |
| T-B16 | `expiresIn` en segundos y forma de `AuthResponse` según ADR-004 | B | P0 | ✅ HECHA (#40) | [VITO-41](https://bernardo-mira.atlassian.net/browse/VITO-41) |
| T-B17 | `/events/upcoming` lista eventos privados ajenos (filtro `settings.isPublic`) | B | P0 | 🟢 READY | [VITO-56](https://bernardo-mira.atlassian.net/browse/VITO-56) |
| T-B03 | Envelope ADR-003 en los 6 servicios sin handler (copia temporal, ADR-017) | B | P1 | 🟢 READY | [VITO-28](https://bernardo-mira.atlassian.net/browse/VITO-28) |
| T-B18 | Parent POM + `backend/shared-web`; borrar copias de filtro JWT y handler (ADR-017) | B/I | P1 | 🔴 BLOQUEADA (Ola 1) | — |
| T-I07 | `docker-compose.prod.yml` + Caddy + límites de memoria (ADR-011) | I | P0 | ✅ HECHA (#36) | [VITO-46](https://bernardo-mira.atlassian.net/browse/VITO-46) |
| T-I09 | Backup diario de Mongo a almacenamiento S3 (R2) + restauración | I | P0 | ✅ HECHA (#35) | [VITO-48](https://bernardo-mira.atlassian.net/browse/VITO-48) |
| T-I10 | media-service con endpoint S3 configurable (R2 en prod, SeaweedFS en local) | B | P0 | ✅ HECHA (#34) | [VITO-49](https://bernardo-mira.atlassian.net/browse/VITO-49) |
| T-I12 | CI solo construye y prueba lo que cambió (filtros `paths`) | I | P1 | 🟢 READY | [VITO-51](https://bernardo-mira.atlassian.net/browse/VITO-51) |
| T-I06 | Aprovisionar cuentas y VPS (tarea manual de leads) | I | P0 | 🟢 READY (manual, leads) | [VITO-45](https://bernardo-mira.atlassian.net/browse/VITO-45) |
| T-I08 | Pipeline de despliegue a producción por SSH | I | P0 | 🔴 BLOQUEADA (T-I06) | [VITO-47](https://bernardo-mira.atlassian.net/browse/VITO-47) |
| T-I11 | Frontend web en Cloudflare Pages | I/F | P1 | 🔴 BLOQUEADA (T-I06) | [VITO-50](https://bernardo-mira.atlassian.net/browse/VITO-50) |
| T-I13 | Grafana Cloud: métricas, logs y uptime | I | P1 | 🔴 BLOQUEADA (T-I06) | [VITO-52](https://bernardo-mira.atlassian.net/browse/VITO-52) |
| T-I14 | Runbook de restauración y de incidentes | I | P1 | 🟢 READY | [VITO-53](https://bernardo-mira.atlassian.net/browse/VITO-53) |
| T-I15 | Protocolo de bloqueos entre agentes (frontend ↔ backend/infra) | I | P1 | 🟢 READY | [VITO-54](https://bernardo-mira.atlassian.net/browse/VITO-54) |
| T-I16 | Protección de rama en `main` y `develop` (tarea manual de leads) | I | P0 | ✅ HECHA (#49, manual) | [VITO-55](https://bernardo-mira.atlassian.net/browse/VITO-55) |

Bloqueadas (no tomar): T-C07 (VITO-16), T-F02 (VITO-19), T-F05 (VITO-22), T-F08 (VITO-25), T-B06 (VITO-31), T-B07 (VITO-32), T-B10 (VITO-35), T-B13 (VITO-38), T-B18, T-I08 (VITO-47), T-I11 (VITO-50), T-I13 (VITO-52).
Reemplazadas por ADR-011: T-I04 (→ T-I08) y T-I05 (Kubernetes → Fase Crecimiento).

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

### T-C02 · `package.json` + mock REST (Prism) — ✅ HECHA (#27) · P0 · O-04
- **Qué:** `package.json` en la raíz con `@stoplight/prism-cli`, `@stoplight/spectral-cli`,
  `@asyncapi/cli` fijados y scripts `lint:api`, `lint:async`, `mock:api` (Prism en :4010).
  `mock:ws` queda como script que falla con mensaje "pendiente de T-C05".
- **Cierre:** `npm ci && npm run mock:api` sirve las 15 operaciones; Flutter corre contra
  `http://localhost:4010/api/v1` (comando ya documentado en CONTRIBUTING §6).

### T-C03 · Job de CI "contract" — ✅ HECHA (#44) · P0 · Principio I
- **Qué:** job en `.github/workflows/ci.yml` que corre `npm run lint:api` y un script que
  valida cada fixture contra su schema (p. ej. `ajv` sobre los schemas de OpenAPI).
  `lint:async` se activa cuando exista `asyncapi.yaml`.
- **Cierre:** el job es requerido en los PR que tocan `docs/api/**` y está verde.

### T-C04 · Tests Dart contra fixtures — ✅ HECHA (#31) · P0 · Principio IV
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

### T-C08 · Contrato de auth alineado con ADR-004 — ✅ HECHA (#45) · P0 · ADR-004, Principio I
- **Hallazgo (T-B15/T-B16):** ADR-004 (aceptada) fija la respuesta de `register`/`login`/
  `refresh` con `user` **plano** (`id, username, email, displayName, avatar`), **sin**
  `tokenType`, y `logout` → `204`; sus consecuencias pedían corregir `openapi.yaml`, pero no
  se hizo. T-C01 (fixtures) y T-C04 (tests Dart) siguieron al openapi sin corregir: `user`
  con `profile`/`stats`, `tokenType` y `logoutUser` `200`. El backend ya devuelve la forma
  de ADR-004, así que Flutter pierde `displayName`/`avatar` (los busca en `profile`).
  **Decisión de leads (2026-10-17): se mantiene ADR-004.**
- **Qué:** issue `contract-change` y PR `contract/auth-adr-004`:
  - `openapi.yaml`: `AuthResponse` sin `tokenType` y con `user` = nuevo schema `AuthUser`
    plano (el schema `User` completo queda para social); `logoutUser` → `204`.
  - Fixtures `authentication/*` 2xx con esa forma; `logoutUser.200.json` → `logoutUser.204`.
  - Flutter: modelo de usuario de auth que lea la forma plana; tests de T-C04 actualizados.
- **Desbloquea:** que T-F01 muestre nombre y avatar tras el login contra el backend real.
- **Cierre:** Spectral verde; tests Dart de contrato verdes con los fixtures nuevos; PR
  aprobado por 1 CODEOWNER.

### T-C06 · `listEvents` con `scope` (O-26) — 🟡 RFC · P0 · ADR-014
- **Qué:** issue `contract-change` y PR `contract/events-listing`:
  - `openapi.yaml`: parámetro `scope` (enum `mine|upcoming`, default `mine`) en
    `listEvents`; descripción de contenido y orden de cada scope (en `upcoming`, orden por
    `details.startDate` ascendente, excepción a CONVENTIONS §5); `400` con
    `ErrorResponse` para un `scope` desconocido o `size` > 100.
  - Fixtures `listEvents` para `mine`, `upcoming` y el `400`.
- **No hacer:** publicar `/events/my-events` ni `/events/upcoming`; agregar parámetros de
  identidad (`organizerId`, `userId`).
- **Desbloquea:** T-B07, T-F08.
- **Cierre:** Spectral y job Contract verdes; PR aprobado por 1 CODEOWNER.

### T-C07 · Ramas `contract/ola-N` (1 a 6) — 🔴 BLOQUEADA por D-01 + D-08 (por ola) · O-02, O-30
- **Qué:** por cada ola aprobada en `endpoint-inventory.md`: paths + schemas en
  `openapi.yaml`, frames/eventos en `asyncapi.yaml`, fixtures, ADR si aplica y handoff
  `docs/handoff/<ola>.md` desde `TEMPLATE.md`. Se ejecuta con Spec Kit
  (`/speckit-specify` referenciando la ola y las O-xx que cierra).

---

## Pista F — Frontend Flutter

### T-F01 · AuthBloc real — ✅ HECHA (#46, #56) · P0 · O-20
- **Qué:** quitar el `// MOCK` de `auth_bloc.dart` (`_onLoginRequested`, `_onAuthCheckRequested`)
  y usar `LoginUseCase`/`RegisterUseCase`/`LogoutUseCase` + persistencia de tokens y
  refresh automático en el interceptor Dio (ADR-004). Solo operaciones del contrato:
  `register`, `login`, `refresh`, `logout`.
- **Auto-login:** con lo que hay en contrato, se resuelve con el refresh token guardado
  (`POST /auth/refresh`) y el `user` del `AuthResponse`. `GET /auth/me` **no** se usa
  hasta la Ola 1.
- **Cierre:** sin datos hardcodeados en `features/auth`; tests de bloc para éxito, 401,
  400 y refresh; funciona contra `npm run mock:api`.
- **Complemento (#56):** el splash decidía a ciegas; ahora navega según la sesión
  restaurada, `POST /auth/register` deja de enviar `displayName` (fuera de
  `RegisterRequest`; hallazgo para RFC) y los flujos se verifican contra el mock.

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
- **Chat (ADR-015):** `chat_remote_datasource.dart` hace lo mismo y **retira `?userId=`**;
  `connect` deja de recibir el `userId`.
- **Cierre:** test unitario que verifica la URL generada y el manejo de 4401.

### T-F05 · Subida al mosaico — 🔴 BLOQUEADA por D-01 + Ola 3
### T-F08 · Lista "mis eventos" — 🔴 BLOQUEADA por T-C06 · ADR-014
- **Qué:** `getMyEvents` envía `scope=mine`; `getUpcomingEvents` deja de llamar a
  `/events/upcoming` (fuera de contrato) y usa `GET /events?scope=upcoming`.
- **Cierre:** tests del datasource contra los fixtures de T-C06.

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

### T-B02 · Gateway: envelope ADR-003 — ✅ HECHA (#38) · P1 · O-10
- **Qué:** el 401 del filtro y `FallbackController` devuelven
  `{error, message, status, timestamp}` (`UNAUTHORIZED`, `SERVICE_UNAVAILABLE`).
- **Cierre:** test de cada respuesta comparada con el schema `ErrorResponse`.

### T-B03 · ADR-003 en los 6 servicios sin handler — 🟢 READY · P1 · O-29, ADR-017
- Servicios sin `@RestControllerAdvice`: social, event, checkin, mosaic, streaming, archive.
- **Qué:** copiar el `GlobalExceptionHandler` y el `ErrorResponse` de `auth-service` (ya
  alineados a ADR-003 por T-B15), adaptando solo el paquete y las excepciones de dominio de
  cada servicio.
- **No hacer:** crear `backend/shared-*` ni cambiar POMs, Dockerfile o CI (es T-B18);
  modificar el handler de auth, chat o media.
- **PR:** declara la desviación del Principio II citando ADR-017 (*Complexity Tracking*).
- **Cierre:** en los 6 servicios, test MockMvc de 400, 404 y 500 con el envelope
  `{error,message,status,timestamp}` y códigos `SCREAMING_SNAKE` de CONVENTIONS.

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

### T-B06 · Chat a WS crudo — 🔴 BLOQUEADA por T-C05 · P1 · ADR-006, ADR-015, O-28
- Reemplazar STOMP/SockJS de `chat-service` por handler crudo con los frames de
  `asyncapi.yaml`.
- Identidad según ADR-015: `HandshakeInterceptor` que copia `X-User-Id` a la sesión y
  cierra con `4401` si falta; se ignora `userId` en query y frames; cada frame de sala
  verifica que el usuario participe en ella.

### T-B07 · Implementar listado de eventos — 🔴 BLOQUEADA por T-C06 · P0 · O-26, ADR-014
- **Qué:** `GET /events?scope=mine|upcoming` en event-service según ADR-014:
  identidad solo de `X-User-Id`; `mine` por defecto; `400 VALIDATION_ERROR` para un
  `scope` desconocido o `size` > 100; `forViewer` aplicado a cada evento listado.
  Índices `{organizerId: 1, createdAt: -1}` y
  `{status: 1, "settings.isPublic": 1, "details.startDate": 1}` en `01-init.js`, retirando
  los de `schedule.start` y `location.coordinates`, que son anteriores a ADR-001.
  Retirar `/my-events` y `/upcoming` cuando T-F08 esté mergeada.
- **Cierre:** tests de los dos scopes, del `400` y de que `upcoming` no incluye eventos
  privados; respuestas iguales a los fixtures de T-C06.

### T-B08 · `hideAddressUntilAccepted` — ✅ HECHA (#26) · P1 · ADR-001, Principio IX, O-33
- **Qué:** el campo existe en contrato, ADR-001 y Flutter, pero no en
  `Event.EventSettings` de event-service. Agregarlo, persistirlo y, en `GET /events/{id}`,
  omitir `details.address`/`latitude`/`longitude` si es `true` y quien consulta no es
  el organizador ni tiene RSVP `ACCEPTED`.
- **Cierre:** tests de dominio para organizador / aceptado / no aceptado; respuesta igual
  al fixture `getEvent` de T-C01.

### T-B09 · FastAPI en compose — ✅ HECHA (#43) · P1 · O-31
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

### T-B12 · `isPublic` con el nombre del contrato — ✅ HECHA (#28) · P0 · Principio I, ADR-001
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

### T-B13 · Mismo defecto de `is*` en otros servicios — 🔴 BLOQUEADA por T-C07 (olas 2–5) · P1 · Principio I
- **Hallazgo (T-B12):** los mismos `boolean is*` primitivos con Lombok salen sin el prefijo
  `is` en JSON y se ignoran al recibirlos. El frontend espera el nombre con `is`:
  `EventArchive.EventArchiveSettings.isPublic` (archive), `MosaicFeedItem.isLiked` (mosaic),
  `SocialProfile.isFollowing` / `isFollowedBy` (social), `ChatMessage.isDeleted` (streaming).
  Los `Boolean` envueltos (p. ej. `ArchiveService.*Command.isPublic`) no tienen el problema.
- **Qué:** al contractualizar cada servicio en su ola, aplicar el mismo patrón de T-B12
  (`@JsonMixin` en `infrastructure`) con el nombre que fije el contrato.
- **Cierre:** test `@JsonTest` por servicio contra el fixture de su ola.

### T-B15 · Códigos y errores de auth-service y `createEvent` según los fixtures — ✅ HECHA (#39) · P0 · ADR-003, ADR-005, ADR-009, Principio I
- **Hallazgo (T-I07):** probando el stack de producción, `POST /events` respondió `200`
  (contrato: `201`) y un registro duplicado respondió `400 "Validation Error"`
  (contrato: `409 CONFLICT`). El `GlobalExceptionHandler` de auth-service, citado en el
  AGENT-BRIEF como referencia, no sigue ADR-003: `error` en texto libre ("Validation Error",
  "Authorization Error"…), mensajes en inglés y `timestamp` `LocalDateTime` sin zona.
  T-B03 no lo cubre (solo trata los servicios **sin** handler).
- **Qué:**
  - event-service: `createEvent` → `201 Created`.
  - auth-service: duplicado de email o username → `409` `CONFLICT` (excepción de dominio
    propia, no `IllegalArgumentException`); validación → `400` `VALIDATION_ERROR` con
    `details` por campo; resto de códigos `SCREAMING_SNAKE` de CONVENTIONS; mensajes es-ES;
    `timestamp` ISO-8601 UTC (`Instant`).
- **No hacer:** tocar los servicios de T-B03 ni cambiar el contrato.
- **Desbloquea:** que T-F01 pruebe 400/409/401 contra el backend real y no solo contra Prism.
- **Cierre:** tests MockMvc de `registerUser` (201, 400, 409), `loginUser` (200, 401) y
  `createEvent` (201) comparados con sus fixtures de `docs/api/fixtures/` (mismas claves,
  mismos `status` y `error`).
- **Nota (decisión de leads, 2026-10-17):** donde `openapi.yaml` contradice a ADR-004
  (respuesta 2xx de auth y `logoutUser`), manda ADR-004: logout sigue en `204`. El
  contrato se corrige en T-C08.

### T-B16 · `expiresIn` en segundos y forma de `AuthResponse` según ADR-004 — ✅ HECHA (#40) · P0 · ADR-004, Principio I
- **Hallazgo (T-B15):** `expiresIn` sale en **milisegundos** (`86400000`, el valor de
  `jwt.expiration`); ADR-004 y `openapi.yaml` lo definen en **segundos**.
  La forma de la respuesta (`user` plano, sin `tokenType`) ya cumple ADR-004, pero ningún
  test la fija.
- **Qué:** `expiresIn` en segundos en `register`, `login` y `refresh`; test que fije la forma
  de ADR-004: `{accessToken, refreshToken, expiresIn, user{id, username, email,
  displayName, avatar}}`, sin `tokenType`.
- **No hacer:** cambiar la forma de la respuesta ni `openapi.yaml` (eso es T-C08).
- **Cierre:** test de la respuesta de las tres operaciones con `expiresIn` igual a
  `jwt.expiration / 1000`.

### T-B17 · Fuga de eventos privados en `/events/upcoming` — 🟢 READY · P0 · Principio III, ADR-014
- **Hallazgo (D-03):** `EventMongoAdapter.findUpcomingEvents` filtra por
  `status = PUBLISHED` y `details.startDate` ≥ ahora, pero no por `settings.isPublic`.
  Cualquier usuario autenticado lista eventos privados ajenos (título, fechas, descripción).
  La ruta está expuesta hoy por el gateway.
- **Qué:** agregar `settings.isPublic = true` a la consulta y limitar `size` a 100 en
  `/my-events` y `/upcoming`. No cambia rutas ni contrato: es un arreglo de seguridad sobre
  el código actual, previo a T-B07.
- **Cierre:** test de que `/events/upcoming` no devuelve un evento privado publicado y
  futuro de otro organizador; test de `size` > 100.

### T-B18 · Librería `backend/shared-web` — 🔴 BLOQUEADA por la implementación de la Ola 1 · P1 · O-23, ADR-017
- **Qué:** parent POM Maven multi-módulo en `backend/` con el módulo `shared-web`
  (`JwtAuthenticationFilter`, `ErrorResponse`, handler base de ADR-003). Los servicios lo
  consumen como dependencia; `backend/Dockerfile` usa `backend/` como contexto; los jobs
  `backend` y `docker` de `ci.yml` y la matriz de `cd.yml` construyen desde el parent.
  Se borran todas las copias.
- **Coordinar con T-I12:** los filtros `paths` de cada servicio incluyen `backend/shared-web/**`.
- **Plazo (ADR-017):** se toma antes de implementar la Ola 2. Se adelanta si aparece una
  tercera clase compartida.
- **Cierre:** ninguna clase duplicada entre servicios (`JwtAuthenticationFilter`,
  `ErrorResponse`, handler); CI y build de imágenes verdes para los 10 servicios.

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

### T-I04 · CD por servicio — ⚪ REEMPLAZADA por T-I08 (ADR-011)
- `cd.yml` construye auth sin `file: backend/Dockerfile`, con el mismo nombre de imagen que
  transcode, y los deploy son `echo`. ADR-011 elimina el registro de imágenes: se rehace
  completo en T-I08.

### T-I05 · Kubernetes / despliegue — ⚪ DIFERIDA a Fase Crecimiento (ADR-011)
- Los manifiestos de `infrastructure/kubernetes/` no se mantienen en Fase Tracción. No
  borrarlos: se retoman al cumplir el disparador de ADR-011.

### T-B14 · Actuator, health y métricas en todos los servicios — ✅ HECHA (#33) · P0 · O-11, Principio VI
- **Qué:** `spring-boot-starter-actuator` + `micrometer-registry-prometheus` en los 10
  servicios Java, con `/actuator/health/liveness`, `/actuator/health/readiness` y
  `/actuator/prometheus` (no expuestos por el gateway). En transcode y moderation, endpoint
  `/health` y métricas con `prometheus-client`.
- **Por qué ahora:** los healthchecks de `docker-compose.prod.yml` (T-I07) y Grafana Cloud
  (T-I13) dependen de esto.
- **Cierre:** test por servicio que verifica `200` en liveness/readiness y que
  `/actuator/prometheus` responde.

### T-I06 · Aprovisionar cuentas y VPS — 🟢 READY (manual, leads) · P0 · ADR-011, ADR-013
- **Tarea manual de los leads** (no la ejecuta un agente):
  0. En GitHub, cambiar la rama por defecto a `develop` (ADR-012).
  1. Correo de equipo, gestor de contraseñas con 2FA, tarjeta y hoja de gastos (ADR-013).
  2. Contabo Cloud VPS 12 GB en EE. UU.; acceso solo por llave SSH, sin login de root por
     contraseña; firewall con 22 (restringido), 80, 443, 1935 (RTMP) y los puertos UDP de
     WebRTC de SRS.
  3. Cloudflare: dominio, DNS, bucket R2 (`vito-media`, `vito-backups`) y token de API
     limitado a esos buckets.
  4. GitHub Environment `production` con `SSH_HOST`, `SSH_USER`, `SSH_KEY` y los secretos de
     `.env.example`.
  5. Completar el inventario de cuentas de ADR-013.
- **Cierre:** `ssh` con llave funciona, `docker compose version` en el VPS, buckets creados
  y secretos cargados en GitHub. Registrar el gasto en la hoja de D-09.

### T-I07 · `docker-compose.prod.yml` — ✅ HECHA (#36) · P0 · O-13, ADR-011
- **Qué:** archivo Compose de producción, separado del de desarrollo:
  - los servicios de ADR-011 (10 Java, transcode, moderation, Mongo, Redis, Kafka KRaft de un
    broker, SRS, Caddy); **sin** recommendation ni SeaweedFS;
  - `mem_limit` por servicio y `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` (o equivalente)
    para que el total quepa en 12 GB con margen; documentar la tabla de memoria;
  - imágenes con tag `${IMAGE_TAG}` (SHA), nunca `:latest`;
  - solo Caddy (80/443) y SRS publican puertos; Mongo, Redis y Kafka con autenticación y
    sin puertos en el host;
  - `Caddyfile`: TLS automático, `api.<dominio>` → gateway (incluido WebSocket);
  - healthchecks usando T-B14.
- **Cierre:** `docker compose -f docker-compose.prod.yml config` válido;
  `docker compose ... up` en local con un `.env` de prueba levanta todo dentro de 12 GB
  (`docker stats` en el PR).

### T-I08 · Pipeline de despliegue — 🔴 BLOQUEADA por T-I06 · P0 · O-12, ADR-011, ADR-012
- **Qué:** rehacer `cd.yml`: al entrar un commit a `main`, construir solo las imágenes de
  los servicios cambiados (tag = SHA), copiarlas al VPS con `docker save | ssh docker load`,
  ejecutar `docker compose up -d` con el nuevo `IMAGE_TAG` y verificar los healthchecks.
  Rollback manual con `workflow_dispatch` indicando un SHA anterior.
- **Cierre:** un merge a `main` despliega sin intervención; el rollback a un SHA anterior
  funciona y queda documentado en el runbook.

### T-I09 · Backup de Mongo a R2 — ✅ HECHA (#35) · P0 · Principio IX, ADR-011
- **Qué:** contenedor o cron que corre `mongodump --gzip` diario y sube el archivo a un
  bucket S3 (R2 en producción) con retención de 14 días. Script de restauración.
- **Verificación local:** contra SeaweedFS (API S3) en el compose de desarrollo.
- **Cierre:** el backup se genera y sube; la restauración en una base vacía recupera los
  datos (pasos y salida en el PR).

### T-I10 · media-service con endpoint S3 configurable — ✅ HECHA (#34) · P0 · O-32, ADR-011
- **Qué:** endpoint, región, bucket y credenciales S3 por variables de entorno, con
  path-style configurable. Sin cambios de contrato. Igual para transcode y archive si
  acceden al almacenamiento.
- **Cierre:** test de integración contra SeaweedFS local; documentación de las variables
  para R2 en `.env.example`.

### T-I11 · Frontend web en Cloudflare Pages — 🔴 BLOQUEADA por T-I06 · P1 · ADR-011
- **Qué:** reemplazar el deploy a Vercel de `deploy-frontend.yml` por Cloudflare Pages
  (`wrangler pages deploy`), con las mismas puertas de calidad y el build estricto de T-F06.
- **Cierre:** un merge a `main` publica la web en Pages con `API_BASE_URL`/`WS_BASE_URL` de
  producción.

### T-I12 · CI incremental — 🟢 READY · P1 · ADR-011
- **Qué:** que `ci.yml` construya y pruebe solo los servicios cuyos archivos cambiaron
  (filtros `paths` o `dorny/paths-filter`), más un job semanal completo. El plan gratuito de
  GitHub Actions da 2.000 minutos al mes en repos privados.
- **Cierre:** un PR que solo toca `frontend/` no ejecuta jobs de backend, y viceversa.

### T-I13 · Observabilidad con Grafana Cloud — 🔴 BLOQUEADA por T-I06 · P1 · Principio VI
- **Qué:** Grafana Alloy (u OTel Collector) en el VPS que envía métricas, logs y trazas a
  Grafana Cloud free; dashboard RED del gateway; alertas de caída y de disco/RAM > 80 % a
  ambos leads; monitor de uptime externo.
- **Cierre:** dashboard con datos reales y una alerta de prueba recibida.

### T-I14 · Runbooks — 🟢 READY · P1 · Principio IX
- **Qué:** `docs/runbooks/restore.md` (reconstruir el VPS desde cero con Compose + `.env` +
  backup de R2) y `docs/runbooks/incident.md` (qué mirar, cómo hacer rollback, a quién avisar).
- **Cierre:** un ensayo de restauración completo en una máquina limpia, con tiempos medidos
  (RTO ≤ 4 h).

### T-I15 · Protocolo de bloqueos entre agentes — 🟢 READY · P1 · CONTRIBUTING (cadencia)
- **Por qué:** el agente frontend y el backend/infra trabajan en paralelo. Ya existen las
  etiquetas `blocked-by-backend` / `blocked-by-frontend` y el sync de los miércoles, pero
  ningún documento le dice a un agente cuándo y cómo usarlas, ni cómo pedir que se adelante
  una tarea de la otra pista.
- **Qué:**
  - AGENT-BRIEF §9: si una tarea depende de otra pista, el agente abre un issue con la
    etiqueta `blocked-by-<pista>` que cita la tarea bloqueada y la que propone adelantar
    (T-xx), el impacto y si hay un rodeo temporal (p. ej. Prism). No implementa la otra pista.
  - Plantilla de issue `.github/ISSUE_TEMPLATE/blocked.md` con esos campos.
  - AGENT-BRIEF §7: al tomar una tarea, revisar primero los issues `blocked-by-<mi pista>`
    abiertos; atenderlos tiene prioridad sobre las tareas P1/P2 de la propia pista.
  - Los leads repriorizan en `tasks.md` en el sync del miércoles; el agente no cambia la
    prioridad de tareas ajenas.
- **Cierre:** la plantilla aparece al crear un issue en GitHub; AGENT-BRIEF y CONTRIBUTING
  enlazan el flujo; un issue de ejemplo (T-F01 ← T-B02/T-B15) creado con la plantilla.

### T-I16 · Protección de rama en `main` y `develop` — ✅ HECHA (#49, manual) · P0 · ADR-012, CONTRIBUTING §1
- **Tarea manual de los leads** (requiere admin del repo; no la ejecuta un agente).
- **Por qué:** el repo pasó a ser público el 2026-10-19 y el plan Free ya permite la
  protección de rama. Hasta ahora, "sin push directo" y "1 aprobación de CODEOWNER" se
  cumplían solo por acuerdo, y #20 y #46 terminaron mergeados fuera de `develop`.
- **Qué:** aplicar en las dos ramas las reglas de CONTRIBUTING §1: PR obligatorio también
  para admins, 1 aprobación de CODEOWNER, los 25 checks de `ViTo CI`, sin force push ni
  borrado.
  - `develop`: ✅ aplicada el 2026-10-19.
  - `main`: ✅ aplicada el 2026-10-19; reglas idénticas a las de `develop`.
  - Documentación: ✅ #49.
- **Cierre:** `gh api repos/AppViTo/ViTo/branches/main --jq .protected` devuelve `true` y
  las reglas de `main` coinciden con las de `develop`.
