<!--
Sync Impact Report — v1.3.0 → v1.4.0 (MINOR) · 2026-10-30
- Modificado: Principio X, stack canónico. Spring Boot 3.x LTS → Spring Boot 4.0.x sobre
  Java 21, según ADR-018 (Propuesta, PR #223). La línea 3.x deja de ser la canónica.
- Entrada: esta enmienda se mergea junto con la aprobación de ADR-018. Sin ese ADR, el
  stack canónico queda sin decisión aprobada.
- Observaciones: ninguna cerrada aquí. O-21 y O-35 se cierran en una enmienda PATCH al
  completar T-B31.
- Plantillas: .specify/templates/* sin cambios requeridos.

Sync Impact Report anterior — v1.2.1 → v1.3.0 (MINOR) · 2026-10-22
- Modificado: Principio VIII, Fase Tracción. Producción requiere, además del PR aprobado
  que entra a `main`, la aprobación de un lead en el GitHub Environment `production`
  protegido. Es posible porque el repo es público desde el 2026-10-19 (ADR-011,
  actualización del 2026-10-22).
- Observaciones: O-24 cerrada (protección de rama activa en `main` y `develop` desde el
  2026-10-19, sin GitHub Team).
- Plantillas: .specify/templates/* sin cambios requeridos.

Sync Impact Report anterior — v1.2.0 → v1.2.1 (PATCH) · 2026-10-17
- O-24 cerrada en su parte de flujo por ADR-012 (ramas y release); sigue pendiente la
  protección de rama, que depende del plan de GitHub.

Sync Impact Report anterior — v1.1.0 → v1.2.0 (MINOR) · 2026-10-17
- Reemplazado: "Reglas en Revisión por Costo" → "Perfiles de Despliegue" (Fase Tracción
  vigente según ADR-011; Fase Crecimiento = reglas originales, activadas por disparadores).
- Modificado: Principio V (replicación de tópicos según perfil), VII (carga de medición
  según perfil), VIII (despliegue y promoción según perfil; frontend en Cloudflare Pages),
  IX (MongoDB según perfil), X (stack canónico por perfil).
- Modificado: Production Readiness Review se mide contra el perfil vigente.
- Observaciones: O-03, O-13, O-17 y O-32 resueltas o redefinidas por ADR-011.
- Historial: v1.1.0 ratificada el 2026-10-16 (PR #17); v1.0.0 en PR #15.
- Plantillas: .specify/templates/* sin cambios requeridos.
-->

# ViTo (VibeTogether) Constitution

Constitución técnica de ViTo, plataforma de ciclo de vida de eventos sociales (eventos,
invitaciones, RSVP, QR/check-in, mosaico en vivo, streaming, chat y archivo post-evento).
Su objetivo es llevar el sistema actual — un prototipo funcional de microservicios — a un
estado **apto para producción**. Todo `spec.md`, `plan.md` y `tasks.md` generado con Spec Kit
DEBE cumplirla. Usa las palabras clave MUST / SHOULD / MAY en el sentido de RFC 2119
(DEBE / DEBERÍA / PUEDE).

## Core Principles

### I. Contrato Primero (NO NEGOCIABLE)

- `docs/api/openapi.yaml` (REST) y `docs/api/asyncapi.yaml` (WebSocket **y** Kafka) son la
  única fuente de verdad de toda interfaz entre componentes. Ningún endpoint, frame WS ni
  evento Kafka se implementa si no existe primero en el contrato (ADR-010: el código backend
  es referencia, no fuente de verdad).
- Todo cambio de contrato sigue el proceso RFC de `CONTRIBUTING.md` §4: issue
  `contract-change` → rama `contract/<nombre>` → aprobación de **al menos 1 lead** (CODEOWNER, distinto del autor) → ADR si la
  decisión es relevante → implementación.
- Cada operación DEBE tener `operationId`, ejemplos, `ErrorResponse` en 4xx/5xx y fixture
  en `docs/api/fixtures/`. Las convenciones de `docs/api/CONVENTIONS.md` y los ADR 001–010
  son vinculantes (lista plana, envelope de error único `{error,message,status,timestamp}`,
  paginación `page`/`size`, `400` validación vs `409` duplicado, WS crudo, headers `X-User-*`).
- El contrato DEBE validarse en CI: Spectral sobre OpenAPI, `@asyncapi/cli validate` sobre
  AsyncAPI y `schemathesis` contra cada servicio desplegado en entorno efímero. Una
  divergencia código↔contrato bloquea el merge.
- Un endpoint existente en el código y ausente del contrato se considera **no publicado**:
  no se enruta en el gateway de producción hasta que se contractualice o se elimine.

**Rationale:** dos equipos (Flutter y backend) trabajan en paralelo; el contrato es lo que
les permite integrarse sin coordinarse línea a línea. Hoy hay ~93 mappings REST en el código
frente a 14 rutas en `openapi.yaml`; sin esta regla producción expondría una API no revisada.

### II. Arquitectura Hexagonal y Servicios Autónomos

- Cada servicio Spring Boot DEBE respetar las capas `domain` (modelo, puertos, excepciones;
  sin dependencias de Spring, Mongo ni Kafka) → `application` (casos de uso) →
  `infrastructure` (adaptadores inbound/outbound, config, seguridad). Las dependencias
  apuntan siempre hacia el dominio; se DEBE verificar con ArchUnit en `mvn verify`.
- Cada servicio es dueño exclusivo de su base de datos MongoDB. Ningún servicio lee ni
  escribe colecciones de otro; la integración es vía API contractualizada o eventos Kafka.
- El frontend Flutter sigue Clean Architecture feature-first (`data` / `domain` /
  `presentation` con BLoC); la UI no llama a Dio ni a WebSocket directamente, solo a casos
  de uso a través de repositorios inyectados con `get_it`.
- Los servicios Python (FastAPI) separan `api` / `services` / `infrastructure` /
  `schemas` y validan toda entrada con Pydantic.
- Código compartido entre servicios (p. ej. el `JwtAuthenticationFilter` hoy duplicado en 7
  servicios, el `ErrorResponse`, el sobre de eventos) DEBE extraerse a una librería interna
  versionada (`backend/shared-*`) en lugar de copiarse.

**Rationale:** la hexagonal ya es la convención del repo; formalizarla y verificarla
automáticamente evita que la presión de llegar a producción la erosione.

### III. Seguridad por Defecto (Zero Trust)

- **Borde único:** todo tráfico externo (REST y WS) entra por `api-gateway`. Los servicios
  internos NO se exponen fuera del clúster. El gateway DEBE eliminar cualquier `X-User-*`
  entrante del cliente en **todas** las rutas (incluidas las públicas) antes de reenviar, y
  DEBE tener ruta `/ws/**` con validación del `?token=` y cierre `4401`.
- **Tokens:** los access tokens DEBEN firmarse con clave asimétrica (RS256/ES256) emitida
  solo por `auth-service` y publicada vía JWKS; los demás servicios solo verifican. Prohibido
  compartir un secreto HMAC entre servicios en producción. Los tokens QR DEBEN usar un par de
  claves distinto (RSA) del de sesión. Revocación (blacklist / rotación de refresh) en
  Redis es obligatoria antes de producción.
- **Defensa en profundidad:** cada servicio DEBE validar igualmente la identidad recibida
  (JWT o mTLS/red de malla) y aplicar autorización por recurso (p. ej. solo el organizador
  edita su evento; solo staff del evento valida QR). `anyExchange().permitAll()` está
  prohibido en producción.
- **Secretos:** nunca en el repositorio ni en imágenes; se inyectan desde un gestor de
  secretos (Kubernetes Secrets cifrados con Sealed Secrets/External Secrets, o AWS Secrets
  Manager). Los servicios DEBEN fallar al arrancar si falta un secreto (ya es la política de
  `.env.example`; se mantiene).
- **Entradas:** validación con Bean Validation / Pydantic en toda frontera; subida de
  archivos limitada a 10 MB y a los MIME de CONVENTIONS §8, verificando magic bytes, con
  escaneo antimalware y moderación antes de publicar en el mosaico.
- **Transporte y red:** TLS en todo tráfico externo; Kafka con SASL/TLS y ACL por tópico;
  Redis y MongoDB con autenticación y sin puertos públicos; CORS con lista
  explícita de orígenes de producción (no `localhost`); cabeceras de seguridad (CSP, HSTS,
  X-Frame-Options) en el frontend web.
- **Cadena de suministro:** escaneo de dependencias (Dependabot/OWASP/pip-audit), escaneo de
  imágenes (Trivy) y de secretos (gitleaks) en CI; una vulnerabilidad CRITICAL/HIGH sin
  excepción documentada bloquea el despliegue. `backend/SECURITY_CHECKLIST.md` DEBE estar
  sin ítems `TODO` de severidad alta antes del primer release.

**Rationale:** ViTo maneja datos personales, ubicaciones de eventos privados, contenido
multimedia de terceros y control de acceso físico (QR). Un fallo de seguridad aquí tiene
impacto en el mundo real.

### IV. Calidad Verificable y Pruebas Obligatorias

- Las pruebas NO son opcionales. Toda historia de usuario entrega, como mínimo:
  pruebas unitarias del dominio/caso de uso, prueba de contrato (fixture ↔ modelo Dart y
  `schemathesis` ↔ servicio) y prueba de integración del adaptador con dependencias reales
  (Testcontainers para MongoDB, Kafka y Redis; no mocks de infraestructura).
- Para lógica crítica (emisión/validación de QR, control de capacidad, RSVP/waitlist,
  autenticación y refresh) se DEBE escribir la prueba primero y verla fallar (TDD).
- Cobertura mínima de líneas en código nuevo: **80 %** en `domain` + `application` (JaCoCo,
  pytest-cov, `flutter test --coverage`); la cobertura global no puede bajar en un PR.
- Flutter: `flutter analyze --fatal-infos` y `flutter test` en verde; ninguna pantalla en
  producción consume `mock_api_service` ni datos hardcodeados.
- Los servicios Python sin pruebas (`moderation-service`, `recommendation-service`) NO se
  despliegan a producción hasta tener su suite y job de CI.
- Antes de cada release: suite E2E de los flujos MVP (registro → crear evento → invitar →
  RSVP → QR → check-in → subir al mosaico → stream → archivo) y pruebas de carga k6
  (`backend/load-tests/`) contra staging.

**Rationale:** con 10 servicios Java, 3 Python y una app Flutter, solo una red de pruebas
automatizada permite desplegar con frecuencia sin regresiones.

### V. Mensajería Orientada a Eventos Confiable

- Los eventos de dominio publicados en Kafka DEBEN estar declarados en `asyncapi.yaml`
  con un sobre común versionado: `{eventId, type, version, occurredAt (ISO-8601 UTC),
  aggregateId, payload}`. La clave de partición es el `aggregateId` (p. ej. `eventId`).
- Publicar tras persistir DEBE hacerse con **Transactional Outbox** (o equivalente) para
  evitar el doble write inconsistente Mongo↔Kafka; prohibido `kafkaTemplate.send` "dispara y
  olvida" sin manejo del resultado.
- Productores con `acks=all` e idempotencia habilitada; consumidores idempotentes
  (deduplicación por `eventId`), con reintentos acotados y Dead Letter Topic por tópico.
- Cambios incompatibles en un evento ⇒ nueva `version` y coexistencia hasta que todos los
  consumidores migren; nunca se reinterpreta un campo existente.
- Todo tópico se crea de forma declarativa (nombre, particiones, replicación y
  retención) — nunca por auto-creación en producción. Producción usa Kafka en modo KRaft.
  Replicación según el perfil de despliegue: 1 en Fase Tracción (un broker), ≥ 3 en Fase
  Crecimiento.
- Realtime de baja latencia (mosaico, reacciones, check-in en vivo) usa Pub/Sub de
  Redis + WebSocket; Kafka es el registro durable, no el canal de UI.

**Rationale:** hoy 9 servicios publican eventos con payloads ad-hoc (timestamp en epoch,
sin versión) y no existe ningún consumidor Java; formalizarlo ahora es barato, hacerlo con
datos en producción no.

### VI. Observabilidad desde el Día Uno

- Todo servicio (Java y Python) DEBE exponer: `/actuator/health/liveness` y `/readiness`
  (o equivalentes FastAPI), métricas Prometheus (Micrometer / `prometheus-client`) y trazas
  OpenTelemetry propagadas por W3C `traceparent` desde el gateway hasta Kafka y WebSocket.
- Logs estructurados en JSON vía SLF4J/Logback (y `structlog`/logging JSON en Python) con
  `traceId`, `spanId`, `service` y `userId` cuando aplique. Prohibido registrar contraseñas,
  tokens (ni siquiera truncados), QR completos o datos personales sensibles.
- Cada servicio DEBE tener dashboard en Grafana con las métricas RED (rate, errors,
  duration) y alertas sobre los SLO del Principio VII; los dashboards y alertas viven como
  código en el repositorio.
- Un servicio sin health checks funcionando no puede tener `livenessProbe`/`readinessProbe`
  apuntando a endpoints inexistentes: los manifiestos Kubernetes y el código deben coincidir.

**Rationale:** los KPIs del producto (latencia del mosaico, check-in < 200 ms) son
inverificables sin métricas; hoy solo el gateway incluye Actuator.

### VII. Rendimiento Medible contra SLO

Objetivos de servicio vinculantes (p95 salvo indicación), medidos con la carga del perfil
de despliegue vigente: **Fase Tracción** — eventos de 150 asistentes y 100 espectadores por
stream; **Fase Crecimiento** — 500 asistentes concurrentes, en staging:

| Flujo | SLO |
|-------|-----|
| Validación de QR en check-in (servidor) | < 200 ms p99 |
| Propagación de un ítem al mosaico en vivo | < 3 s extremo a extremo |
| Reacciones de stream | < 1 s extremo a extremo |
| Endpoints REST de lectura vía gateway | < 300 ms |
| Disponibilidad mensual del gateway y auth | ≥ 99.5 % |

- El control de capacidad de check-in/RSVP DEBE ser atómico (operación condicional
  `findAndModify` / `$inc` con guarda, o script atómico en Redis); nunca leer-comparar-escribir.
- Toda consulta a MongoDB en rutas calientes DEBE estar respaldada por índice declarado en
  código o en `infrastructure/docker/mongodb/init-scripts` / migraciones.
- Toda lista pagina (`size` máx. 100, CONVENTIONS §5); prohibido devolver colecciones sin
  límite.
- Cada `plan.md` que toque una ruta con SLO DEBE incluir cómo se medirá (escenario k6).

**Rationale:** los KPIs del producto definidos en `AGENTS.md` se convierten aquí en
criterios de aceptación verificables.

### VIII. Entrega Inmutable y Reproducible

- Cada servicio desplegable DEBE tener Dockerfile multi-stage, imagen base mínima,
  usuario no-root, `HEALTHCHECK`, y **un nombre de imagen por servicio** con tag inmutable
  (SHA del commit + semver). Prohibido `:latest` en manifiestos y archivos Compose. La
  distribución de imágenes sigue el perfil de despliegue (Tracción: `docker save | ssh
  docker load`; Crecimiento: registro de contenedores).
- Configuración por variables de entorno (12-factor); los perfiles Spring no contienen
  hosts `localhost` para producción; las URLs entre servicios se resuelven por DNS de la
  plataforma (Docker Compose en Tracción, Kubernetes en Crecimiento).
- **Fase Tracción (ADR-011):** un VPS con `docker-compose.prod.yml`; límites de memoria y
  healthcheck por servicio; solo el borde (Caddy) y SRS exponen puertos; el despliegue sale
  de `main` por GitHub Actions vía SSH; producción requiere dos aprobaciones: la del PR
  que entra a `main` (ADR-012) y la de un lead en el GitHub Environment `production`
  protegido, que el job de despliegue DEBE usar; rollback = redesplegar el SHA anterior.
- **Fase Crecimiento** — despliegue GitOps: manifiestos Kustomize `base` + `overlays/{dev,staging,production}`
  sincronizados por ArgoCD; GitHub Actions construye, prueba, escanea y publica imágenes,
  pero no hace `kubectl apply` contra producción.
- Promoción obligatoria `dev → staging → production`; producción requiere aprobación manual
  (GitHub Environment protegido) y que staging haya pasado E2E + carga. Rollback = revertir
  el commit de versión en el repositorio GitOps.
- Todo Deployment declara `requests`/`limits`, probes, `PodDisruptionBudget`, HPA para
  servicios con estado de carga variable (gateway, mosaic, checkin, streaming) y
  `NetworkPolicy` de denegación por defecto.
- El frontend web (Cloudflare Pages, ADR-011) se despliega solo desde `main` tras pasar las mismas puertas de
  CI y con `API_BASE_URL`/`WS_BASE_URL` de producción obligatorios (el build DEBE fallar si
  están vacíos, nunca caer al default `localhost`). Las apps móviles se versionan con
  semver + build number y se distribuyen por canales de prueba antes de tienda.

**Rationale:** hoy el CD publica dos servicios distintos con el mismo nombre de imagen, los
servicios Java no tienen Dockerfile y los pasos de deploy son `echo`; sin estas reglas no
hay ruta reproducible a producción.

### IX. Datos, Privacidad y Resiliencia

- Datos personales (email, teléfono, ubicación, imágenes de rostros) se tratan conforme a
  la Ley 1581 de 2012 (Colombia) y, para usuarios UE, GDPR: consentimiento explícito,
  minimización, derecho de supresión implementado como flujo (borrado en cascada vía
  evento `USER_DELETED`) y política de retención por tipo de dato.
- Contenido multimedia en S3/MinIO privado; acceso solo por URLs prefirmadas de vida corta.
  La dirección de eventos con `hideAddressUntilAccepted=true` nunca se envía a quien no ha
  aceptado.
- MongoDB en producción: backups automáticos diarios fuera del servidor de base de datos,
  con restauración probada (RPO ≤ 24 h, RTO ≤ 4 h). Fase Tracción: Mongo autoalojado con
  autenticación, `mongodump` diario a R2 y prueba de restauración mensual. Fase
  Crecimiento: replica set (Atlas o equivalente) con cifrado en reposo.
- Comunicación síncrona entre servicios con timeouts, circuit breaker (Resilience4j) y
  reintentos solo en operaciones idempotentes; cada fallback DEBE devolver el envelope de
  error de ADR-003 (`503 SERVICE_UNAVAILABLE`).
- Cambios de esquema de datos se versionan como migraciones (p. ej. Mongock) y son
  compatibles hacia atrás durante al menos una versión desplegada.

**Rationale:** una plataforma de eventos privados con fotos de asistentes es un objetivo
evidente de exposición de datos; la resiliencia evita que un evento en vivo se caiga por un
servicio secundario.

### X. Simplicidad y Stack Canónico

- El stack canónico es: Flutter (Dart) · Spring Boot 4.0.x sobre Java 21 (ADR-018) ·
  FastAPI (Python 3.11+) · MongoDB · Redis · Kafka (KRaft) · Spring Cloud Gateway · SRS
  para media · almacenamiento S3-compatible (Cloudflare R2 en producción, SeaweedFS en
  local) · Docker · GitHub Actions · OpenTelemetry + Grafana. La plataforma de despliegue
  depende del perfil: Docker Compose en un VPS (Tracción) o Kubernetes + ArgoCD
  (Crecimiento). Ver *Perfiles de Despliegue* y ADR-011.
- Añadir una tecnología, servicio o base de datos fuera del stack exige ADR aprobado por
  al menos 1 lead (CODEOWNER) con justificación de por qué lo existente no basta.
- Un servicio nuevo solo se crea si tiene datos propios y ciclo de despliegue
  independiente; si no, es un módulo de un servicio existente (YAGNI).
- Las versiones de framework DEBEN estar dentro de soporte del proveedor; subir de versión
  mayor se planifica como feature con su propio spec.

**Rationale:** 13 servicios para un equipo de 3 personas ya es una superficie operativa
grande; cualquier añadido debe ganarse su lugar.

## Restricciones de Producción y Línea Base del Análisis

Resultado del análisis del repositorio (rama `frontend/harden-vercel-deploy`, commit
`bf587c7`, 2026-10-12). Cada observación referencia el principio que incumple y su
prioridad: **P0** bloquea el primer despliegue a producción, **P1** bloquea el release
público del MVP, **P2** deuda a planificar. Cada observación DEBE convertirse en un spec
(`/speckit-specify`) o en tareas de uno existente; se cierra cuando su criterio se verifica
en CI o staging.

### Inventario actual

| Componente | Estado observado |
|-----------|------------------|
| Backend Java | 10 servicios (gateway, auth, social, event, checkin, mosaic, streaming, archive, chat, media); Java 17; Spring Boot 3.2.1 (10 poms) y 3.5.3 (2); 1–2 clases de test por servicio; `integration-tests` (sin `pom.xml`) y `load-tests` k6 (2); `backend/Dockerfile` multi-stage compartido |
| AI services | transcode (con 3 tests), moderation y recommendation (sin tests ni job de CI); ninguno en `docker-compose.yml` |
| Frontend | Flutter, 10 features, 7 archivos de test (2 sin casos), BLoC + Dio + get_it; despliegue web en Vercel |
| Contrato | `openapi.yaml` con 14 rutas / 15 `operationId` frente a 100 operaciones en código (87 Java + 13 FastAPI, ver `docs/roadmap/endpoint-inventory.md`); 10 ADR aceptados |
| Infra | docker-compose (Mongo, Redis 7.2, Kafka+ZooKeeper, SRS, SeaweedFS, 10 servicios Java en profile `app`); 4 manifiestos K8s base sin overlays |
| CI/CD | CI de 10 servicios Java + transcode + Flutter + build de 11 imágenes; CD con pasos de deploy vacíos; deploy web a Vercel desde `main` |

*(Inventario actualizado el 2026-10-16, commit `1e80ac4`.)*

### Observaciones

| ID | Prioridad | Principio | Observación | Criterio de cierre |
|----|-----------|-----------|-------------|--------------------|
| O-01 | P0 | I | Faltan `docs/api/asyncapi.yaml` y `docs/api/fixtures/`, citados como obligatorios por AGENTS, CONTRIBUTING y CONVENTIONS. | Ambos existen, validan en CI y cubren chat, checkin, mosaic, streaming y Kafka. |
| O-02 | P0 | I | ~93 mappings REST en código vs 14 rutas en `openapi.yaml` (event-service, checkin, social, mosaic, streaming, archive casi sin contrato). | Cada endpoint del gateway de producción tiene `operationId` + fixture, o se retira. |
| O-03 | P2 | X | `AGENTS.md` declara Dragonfly y Eureka; el código usa Redis 7.2 y rutas estáticas. AGENTS lista estructura sin `checkin/mosaic/archive-service`. | ~~ADR que fije cache y discovery~~ (ADR-011: Redis + DNS de Compose); falta AGENTS.md actualizado. |
| O-04 | P2 | I | No existe `package.json` para `npm run mock:api` / `mock:ws` que documenta CONTRIBUTING. | Mocks Prism + WS operativos desde fixtures. |
| O-05 | P0 | III | El gateway no elimina `X-User-*` entrantes en rutas públicas (spoofing de identidad hacia servicios que confían en el header). | Filtro que borra `X-User-*` en todas las rutas + test. |
| O-06 | P0 | III | `SecurityConfig` del gateway usa `anyExchange().permitAll()`; la autorización depende solo de un `GlobalFilter`. | Reglas explícitas por ruta; autorización por recurso en servicios. |
| O-07 | P0 | III | JWT HS256 con un `JWT_SECRET` compartido por todos los servicios: cualquiera puede emitir tokens. QR también simétrico (AGENTS pide RSA). | RS256/ES256 + JWKS; par de claves QR separado. |
| O-08 | P0 | III | No hay ruta `/ws/**` en el gateway (CONVENTIONS §7 la exige). | Ruta WS con validación de token y test de handshake. |
| O-09 | P1 | III | CORS limitado a `localhost`; blacklist de tokens, lockout de cuenta, validación de archivos y escaneo de dependencias aparecen como TODO en `SECURITY_CHECKLIST.md`. | Checklist sin TODO de severidad alta. |
| O-10 | P1 | I/III | El 401 del gateway devuelve `{"error":"<mensaje>","status":401}` sin `message` ni `timestamp` (viola ADR-003). | Envelope ADR-003 en todas las respuestas del gateway y fallbacks. |
| O-11 | P0 | VI | Solo `api-gateway` incluye Actuator; los manifiestos K8s sondean `/actuator/health/*` en servicios que no lo exponen (los pods nunca estarán Ready). Sin Micrometer/Prometheus ni OpenTelemetry. | Actuator + Prometheus + OTel en todos los servicios; probes verdes en staging. |
| O-12 | P0 | VIII | ~~Ningún servicio Java tiene Dockerfile~~ (**parcialmente cerrado** 2026-10-16: `backend/Dockerfile` compartido, CI construye 11 imágenes). Sigue abierto: el CD publica auth y transcode con **el mismo nombre de imagen**, construye auth sin `file: backend/Dockerfile` y los jobs de deploy son `echo`. | Imagen por servicio publicada con tag inmutable y pipeline de despliegue funcional (Fase Tracción: VPS por SSH, ADR-011). |
| O-13 | P0 | VIII | No existe un despliegue de producción reproducible. (Redefinida por ADR-011: los manifiestos Kubernetes pasan a Fase Crecimiento.) | `docker-compose.prod.yml` con límites, healthchecks y Caddy; despliegue desde `main` por SSH; runbook de restauración. |
| O-14 | P1 | VIII | Rutas del gateway con `localhost:808x` en el perfil base; SRS publica el puerto 8080 en compose (colisiona con el gateway). | URLs por DNS de servicio; puertos sin conflicto. |
| O-15 | P1 | VIII | El build web cae a `http://localhost:8080` si faltan `VITO_API_BASE_URL`/`VITO_WS_BASE_URL`. | Build de producción falla si faltan. |
| O-16 | P0 | V | Eventos Kafka publicados "dispara y olvida", sin outbox, sin versión, `timestamp` en epoch (contrario a CONVENTIONS §6); no hay consumidores Java. | Outbox + sobre versionado en AsyncAPI + consumidores idempotentes con DLT. |
| O-17 | P2 | V/X | Kafka con ZooKeeper (cp 7.5); producción debe usar KRaft. | Compose y producción en KRaft (1 broker en Fase Tracción, ADR-011). |
| O-18 | P0 | VII | No se encontró control atómico de capacidad en checkin/event-service (solo social usa `$inc`). | Operación condicional atómica + test de concurrencia. |
| O-19 | P1 | IV | 1–2 tests por servicio; sin Testcontainers, JaCoCo ni ArchUnit; moderation y recommendation sin tests ni CI; `integration-tests` y `load-tests` no corren en CI. | Umbrales del Principio IV aplicados en CI. |
| O-20 | P1 | IV | Flutter incluye `mock_api_service.dart` en `features/home`. | Pantallas de producción conectadas al backend real. |
| O-21 | P1 | X | Spring Boot 3.2.1 y Java 17 en CI; la línea 3.2 ya no recibe soporte OSS. | Spring Boot en versión soportada, Java 21. |
| O-22 | P1 | III/IX | Redis sin `requirepass` efectivo en compose (el `redis.conf` montado no se usa en `command`); Mongo y Kafka exponen puertos en el host. | Auth y red privada en todos los datastores. |
| O-23 | P2 | II | `JwtAuthenticationFilter` duplicado en 7 servicios; `backend/java/` sin uso aparente. | Librería compartida; directorios huérfanos eliminados. |
| O-24 | P1 | — | `CONTRIBUTING.md` exige `develop` pero los PR recientes van a `main`. | ~~Flujo de ramas acordado~~ (ADR-012: feature → `develop`, release semanal a `main`); ~~protección de rama activa con CODEOWNERS~~ (activa desde el 2026-10-19: el repo es público). **Cerrada.** |
| O-25 | P2 | IX | Documentos binarios (`*.docx`) versionados en la raíz; sin política de datos personales ni retención. | Documentos movidos a `docs/` o fuera del repo; política publicada. |
| O-26 | P0 | I | `GET /api/v1/events` (`listEvents`) está en el contrato y lo consume el frontend, pero el backend no lo implementa (solo `/my-events` y `/upcoming`) → 405. | Contrato y backend coinciden (D-03) con fixture y test. |
| O-27 | P0 | III | El rate limiter del gateway toma `X-User-Id` del cliente como clave; en rutas públicas (`/auth/**`) basta rotar el header para evadir el límite de login. | Clave = usuario autenticado por el gateway o IP; test de evasión. |
| O-28 | P0 | III | El WS de chat identifica al usuario por `?userId=` elegido por el cliente; ningún cliente WS envía `?token=`; el chat es STOMP en backend y crudo en frontend (incompatibles). | Identidad tomada del token (D-06); WS crudo en los 4 recursos; handshake vía gateway. |
| O-29 | P1 | I/III | Solo 3 de 9 servicios (auth, chat, media) tienen `@RestControllerAdvice`; los demás devuelven el error por defecto de Spring (viola ADR-003). | Envelope ADR-003 en todos los servicios (D-05). |
| O-30 | P0 | I/II | Dominios duplicados entre servicios: QR/check-in (event vs checkin), subida (media vs mosaic `/photo`), chat (chat vs streaming), info de stream duplicada. | Dueño único por capacidad (D-01, ADR) y rutas sobrantes retiradas. |
| O-31 | P1 | IV/VIII | El gateway rutea recommendation (:8001), moderation (:8002) y transcode (:8000), pero ninguno está en `docker-compose.yml`. | Los 3 en compose con healthcheck, o rutas retiradas. |
| O-32 | P2 | X | Almacenamiento de objetos real = SeaweedFS; constitución y AGENTS dicen S3/MinIO, sin ADR. | ~~ADR de almacenamiento~~ (ADR-011: R2 en producción, SeaweedFS en local); falta media-service configurable por endpoint S3 y docs alineados. |
| O-33 | P1 | IX | `settings.hideAddressUntilAccepted` está en contrato, ADR-001 y Flutter, pero no en el modelo de event-service: la dirección se envía a cualquiera. | Campo persistido y dirección omitida para no aceptados; test. |
| O-34 | P2 | — | Artefactos y archivos sueltos: `backend/out/` sin ignorar, `backend/java/` huérfano, `serve.js`, `Creación`, `img/` en la raíz; README raíz con cola UTF-16. | Repo limpio tras build; README en UTF-8. |
| O-35 | P1 | X | Versiones de Spring Boot mezcladas (3.2.1 ×10, 3.5.3 ×2) sobre Java 17. | Una versión soportada en todos los servicios (amplía O-21). |
| O-36 | P2 | I | `docs/architecture.md` describe `events` en forma plana (contradice ADR-001) y omite checkin/mosaic/archive. | Documento alineado con ADR-001 y el inventario. |

El backlog ejecutable de estas observaciones vive en `docs/roadmap/tasks.md` (cada tarea
cita sus O-xx) y las decisiones abiertas en `docs/roadmap/decisions-pending.md`.

### Perfiles de Despliegue

Las reglas sensibles a costo se aplican según el perfil vigente. **Perfil vigente: Fase
Tracción** (ADR-011, presupuesto 100.000 COP/mes). Se pasa a Fase Crecimiento, total o por
partes, cuando se cumple un disparador de ADR-011 (regla general: los ingresos del mes
cubren dos veces el costo del siguiente nivel). Cada paso se registra como actualización de
ADR-011 y con una enmienda PATCH de esta tabla.

| Tema | Fase Tracción (vigente) | Fase Crecimiento | Principio |
|------|-------------------------|------------------|-----------|
| Plataforma | 1 VPS (Contabo 12 GB, EE. UU.) con Docker Compose | Kubernetes + ArgoCD, Kustomize `base` + `overlays`, HPA, PDB, NetworkPolicy | VIII |
| Ambientes | Producción + CI + Compose local; staging opcional (Oracle Free) | `dev → staging → production` con E2E y carga en staging | VIII |
| Mensajería | Kafka KRaft, 1 broker, replicación 1 | Kafka KRaft, replicación ≥ 3 (gestionado o en clúster) | V |
| MongoDB | Autoalojado, `mongodump` diario a R2, restauración mensual | Replica set gestionado, cifrado en reposo | IX |
| Servicios desplegados | 10 Java + transcode + moderation; recommendation diferido | Según demanda; fusión o división por ADR | X |
| Almacenamiento | Cloudflare R2 (producción), SeaweedFS (local) | R2 u otro S3 según costo | IX, X |
| Caché / discovery | Redis; DNS de Compose | Redis/Valkey gestionado; DNS de Kubernetes | X |
| Observabilidad | Grafana Cloud free + monitor de uptime | Stack propio o plan pago, dashboards y alertas como código | VI |
| Secretos | GitHub Environments + `.env` en el VPS (`chmod 600`) | Sealed Secrets / External Secrets / gestor cloud | III |
| Imágenes | `docker save` → `docker load` por SSH, tag = SHA | Registro de contenedores con tags inmutables | VIII |
| Streaming | SRS en el VPS, sin transcodificar, ≤ 100 espectadores | SRS dedicado o CDN | X |
| Frontend web | Cloudflare Pages | Cloudflare Pages | VIII |
| Carga de referencia (VII) | 150 asistentes, 100 espectadores | 500 asistentes concurrentes | VII |

Siguen plenamente vigentes en ambos perfiles: Principios I, II y IV; en III la prohibición de
secretos en repo/imágenes, TLS, autenticación de datastores sin puertos públicos y el escaneo
de la cadena de suministro; en VI health checks, métricas Prometheus, trazas OTel y logs
JSON; en VII todos los SLO de latencia; en VIII imagen por servicio con tag inmutable, sin
`:latest`, sin `localhost` en producción y build web estricto.

## Flujo de Desarrollo SDD y Puertas de Calidad

**Backlog y priorización:** `docs/roadmap/` (hoja de ruta, `tasks.md`, inventario de
endpoints y decisiones pendientes) es la guía operativa que traduce las observaciones en
trabajo. Un agente solo implementa tareas en estado `READY` y sigue
`docs/handoff/AGENT-BRIEF.md`. Las tareas atómicas (≤ 1 día, sin contrato nuevo) se
implementan directamente; las olas de contrato siguen el ciclo Spec Kit.

**Ciclo por feature (Spec Kit):**

1. `/speckit-specify` — historia de usuario y criterios de aceptación en lenguaje de
   negocio; referencia las observaciones O-xx que resuelve.
2. `/speckit-clarify` — obligatorio si el spec toca contrato, seguridad o datos personales.
3. `/speckit-plan` — DEBE incluir la sección *Constitution Check* evaluando I–X; cualquier
   desviación se registra en *Complexity Tracking* con justificación y se aprueba en el PR.
   Si el plan cambia el contrato, la primera fase es el PR `contract/<nombre>` (Principio I).
4. `/speckit-tasks` — incluye siempre tareas de pruebas (IV), observabilidad (VI) y
   despliegue (VIII) cuando aplique.
5. `/speckit-analyze` antes de implementar; `/speckit-implement` después.

**Ramas y commits:** según `CONTRIBUTING.md` (prefijos `contract/`, `frontend/`, `backend/`,
`docs/`; Conventional Commits; ramas ≤ 7 días; squash merge; sin push directo a `main`).

**Puertas obligatorias de CI para mergear:**

- Compilación + tests + cobertura (IV) de los componentes tocados.
- Lint de contrato (Spectral, AsyncAPI) y prueba de contrato (fixtures / schemathesis).
- ArchUnit (II), escaneo de dependencias, imágenes y secretos (III).
- Revisión aprobada por al menos 1 CODEOWNER de cada ruta tocada, distinto del autor del PR
  (incluye `docs/api/**`, `docs/decisions/**`, infra y CI). Mientras el plan de GitHub no
  permita protección de rama, esta puerta se cumple por acuerdo del equipo.

**Definición de Done:** la de `CONTRIBUTING.md` §5 más: métricas y trazas visibles en
staging, manifiestos/overlays actualizados, y escenario k6 en verde si toca un SLO.

**Production Readiness Review (antes del primer release y de cada servicio nuevo):**
todas las observaciones P0 cerradas, SLO medidos con la carga del perfil vigente (Fase
Tracción: k6 contra Compose local o contra producción antes de abrirla al público; Fase
Crecimiento: en staging), backup restaurado con éxito,
runbook de incidentes por servicio en `docs/runbooks/`, alertas enrutadas a un responsable
de guardia.

## Governance

- Esta constitución prevalece sobre cualquier otra práctica, plantilla o instrucción de
  agente del repositorio. En caso de conflicto: constitución > ADR > `CONVENTIONS.md` >
  contrato (`openapi.yaml` / `asyncapi.yaml`) > `CONTRIBUTING.md` > `docs/roadmap/` >
  `AGENTS.md` > código existente.
- **Enmiendas:** se proponen por PR a `.specify/memory/constitution.md` en rama
  `docs/constitution-<tema>`, con el Sync Impact Report actualizado; requieren aprobación de
  al menos 1 lead (frontend o backend) distinto del autor; el otro lead DEBE quedar como
  revisor solicitado para que esté informado.
- **Versionado semántico:** MAJOR al eliminar o redefinir un principio de forma
  incompatible; MINOR al añadir un principio o sección o ampliar materialmente una regla;
  PATCH en aclaraciones y redacción.
- **Cumplimiento:** cada PR declara en su plantilla qué principios toca; los revisores
  rechazan PRs que los violen sin entrada en *Complexity Tracking*. La tabla de
  observaciones se revisa en la review de integración de cada viernes; al cerrar una
  observación se actualiza su estado en una enmienda PATCH.
- **Revisión periódica:** la constitución completa se revisa al cierre de cada fase del
  cronograma de `AGENTS.md` y antes de cada release mayor.
- Guía operativa en tiempo de desarrollo: `AGENTS.md`, `CONTRIBUTING.md` y
  `docs/api/CONVENTIONS.md`.

**Version**: 1.4.0 | **Ratified**: 2026-10-16 | **Last Amended**: 2026-10-30
