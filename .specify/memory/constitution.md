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
  `contract-change` → rama `contract/<nombre>` → aprobación de **ambos leads** → ADR si la
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
  Redis/Dragonfly es obligatoria antes de producción.
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
  Redis/Dragonfly y MongoDB con autenticación y sin puertos públicos; CORS con lista
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
- Todo tópico se crea de forma declarativa (nombre, particiones, replicación ≥ 3 y
  retención) — nunca por auto-creación en producción. Producción usa Kafka en modo KRaft.
- Realtime de baja latencia (mosaico, reacciones, check-in en vivo) usa Pub/Sub de
  Redis/Dragonfly + WebSocket; Kafka es el registro durable, no el canal de UI.

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

Objetivos de servicio vinculantes (p95 salvo indicación, medidos en staging con carga
representativa de un evento de 500 asistentes concurrentes):

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
  usuario no-root, `HEALTHCHECK`, y se publica en GHCR con **un nombre de imagen por
  servicio** y tag inmutable (SHA del commit + semver). Prohibido `:latest` en manifiestos.
- Configuración por variables de entorno (12-factor); los perfiles Spring no contienen
  hosts `localhost` para producción; las URLs entre servicios se resuelven por DNS de
  Kubernetes (o Eureka si se ratifica).
- Despliegue GitOps: manifiestos Kustomize `base` + `overlays/{dev,staging,production}`
  sincronizados por ArgoCD; GitHub Actions construye, prueba, escanea y publica imágenes,
  pero no hace `kubectl apply` contra producción.
- Promoción obligatoria `dev → staging → production`; producción requiere aprobación manual
  (GitHub Environment protegido) y que staging haya pasado E2E + carga. Rollback = revertir
  el commit de versión en el repositorio GitOps.
- Todo Deployment declara `requests`/`limits`, probes, `PodDisruptionBudget`, HPA para
  servicios con estado de carga variable (gateway, mosaic, checkin, streaming) y
  `NetworkPolicy` de denegación por defecto.
- El frontend web (Vercel) se despliega solo desde `main` tras pasar las mismas puertas de
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
- MongoDB en producción: replica set (Atlas o equivalente), cifrado en reposo, backups
  automáticos diarios con restauración probada al menos una vez por trimestre
  (RPO ≤ 24 h, RTO ≤ 4 h).
- Comunicación síncrona entre servicios con timeouts, circuit breaker (Resilience4j) y
  reintentos solo en operaciones idempotentes; cada fallback DEBE devolver el envelope de
  error de ADR-003 (`503 SERVICE_UNAVAILABLE`).
- Cambios de esquema de datos se versionan como migraciones (p. ej. Mongock) y son
  compatibles hacia atrás durante al menos una versión desplegada.

**Rationale:** una plataforma de eventos privados con fotos de asistentes es un objetivo
evidente de exposición de datos; la resiliencia evita que un evento en vivo se caiga por un
servicio secundario.

### X. Simplicidad y Stack Canónico

- El stack canónico de producción es: Flutter (Dart) · Spring Boot 3.x LTS soportado sobre
  Java 21 · FastAPI (Python 3.11+) · MongoDB · Redis-compatible (Dragonfly o Redis, ver
  TODO) · Kafka (KRaft) · Spring Cloud Gateway · SRS para media · S3/MinIO · Docker ·
  Kubernetes + ArgoCD · GitHub Actions · OpenTelemetry + Prometheus + Grafana.
- Añadir una tecnología, servicio o base de datos fuera del stack exige ADR aprobado por
  ambos leads con justificación de por qué lo existente no basta.
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
| Backend Java | 10 servicios (gateway, auth, social, event, checkin, mosaic, streaming, archive, chat, media) en Spring Boot 3.2.1, Java 17; 1–2 clases de test por servicio; `integration-tests` (6) y `load-tests` k6 (2) |
| AI services | transcode (con 3 tests), moderation y recommendation (sin tests ni job de CI) |
| Frontend | Flutter, 140 archivos Dart, 8 tests, BLoC + Dio + get_it; despliegue web en Vercel |
| Contrato | `openapi.yaml` con 14 rutas / 15 `operationId`; 10 ADR aceptados |
| Infra | docker-compose (Mongo, Redis, Kafka+ZooKeeper, SRS); 4 manifiestos K8s base sin overlays |
| CI/CD | CI de 10 servicios Java + transcode + Flutter; CD con pasos de deploy vacíos |

### Observaciones

| ID | Prioridad | Principio | Observación | Criterio de cierre |
|----|-----------|-----------|-------------|--------------------|
| O-01 | P0 | I | Faltan `docs/api/asyncapi.yaml` y `docs/api/fixtures/`, citados como obligatorios por AGENTS, CONTRIBUTING y CONVENTIONS. | Ambos existen, validan en CI y cubren chat, checkin, mosaic, streaming y Kafka. |
| O-02 | P0 | I | ~93 mappings REST en código vs 14 rutas en `openapi.yaml` (event-service, checkin, social, mosaic, streaming, archive casi sin contrato). | Cada endpoint del gateway de producción tiene `operationId` + fixture, o se retira. |
| O-03 | P2 | X | `AGENTS.md` declara Dragonfly y Eureka; el código usa Redis 7.2 y rutas estáticas. AGENTS lista estructura sin `checkin/mosaic/archive-service`. | ADR que fije cache y discovery; AGENTS.md actualizado. |
| O-04 | P2 | I | No existe `package.json` para `npm run mock:api` / `mock:ws` que documenta CONTRIBUTING. | Mocks Prism + WS operativos desde fixtures. |
| O-05 | P0 | III | El gateway no elimina `X-User-*` entrantes en rutas públicas (spoofing de identidad hacia servicios que confían en el header). | Filtro que borra `X-User-*` en todas las rutas + test. |
| O-06 | P0 | III | `SecurityConfig` del gateway usa `anyExchange().permitAll()`; la autorización depende solo de un `GlobalFilter`. | Reglas explícitas por ruta; autorización por recurso en servicios. |
| O-07 | P0 | III | JWT HS256 con un `JWT_SECRET` compartido por todos los servicios: cualquiera puede emitir tokens. QR también simétrico (AGENTS pide RSA). | RS256/ES256 + JWKS; par de claves QR separado. |
| O-08 | P0 | III | No hay ruta `/ws/**` en el gateway (CONVENTIONS §7 la exige). | Ruta WS con validación de token y test de handshake. |
| O-09 | P1 | III | CORS limitado a `localhost`; blacklist de tokens, lockout de cuenta, validación de archivos y escaneo de dependencias aparecen como TODO en `SECURITY_CHECKLIST.md`. | Checklist sin TODO de severidad alta. |
| O-10 | P1 | I/III | El 401 del gateway devuelve `{"error":"<mensaje>","status":401}` sin `message` ni `timestamp` (viola ADR-003). | Envelope ADR-003 en todas las respuestas del gateway y fallbacks. |
| O-11 | P0 | VI | Solo `api-gateway` incluye Actuator; los manifiestos K8s sondean `/actuator/health/*` en servicios que no lo exponen (los pods nunca estarán Ready). Sin Micrometer/Prometheus ni OpenTelemetry. | Actuator + Prometheus + OTel en todos los servicios; probes verdes en staging. |
| O-12 | P0 | VIII | Ningún servicio Java tiene Dockerfile; el CD publica auth y transcode con **el mismo nombre de imagen**; los jobs de deploy son `echo`. | Dockerfile por servicio, imagen por servicio, pipeline GitOps funcional. |
| O-13 | P0 | VIII | Kubernetes: solo 4 de 13 servicios, imágenes `:latest`, sin overlays, sin Ingress, HPA, PDB ni NetworkPolicy; ArgoCD no configurado. | Kustomize base+overlays completos sincronizados por ArgoCD. |
| O-14 | P1 | VIII | Rutas del gateway con `localhost:808x` en el perfil base; SRS publica el puerto 8080 en compose (colisiona con el gateway). | URLs por DNS de servicio; puertos sin conflicto. |
| O-15 | P1 | VIII | El build web cae a `http://localhost:8080` si faltan `VITO_API_BASE_URL`/`VITO_WS_BASE_URL`. | Build de producción falla si faltan. |
| O-16 | P0 | V | Eventos Kafka publicados "dispara y olvida", sin outbox, sin versión, `timestamp` en epoch (contrario a CONVENTIONS §6); no hay consumidores Java. | Outbox + sobre versionado en AsyncAPI + consumidores idempotentes con DLT. |
| O-17 | P2 | V/X | Kafka con ZooKeeper (cp 7.5); producción debe usar KRaft. | Compose y producción en KRaft. |
| O-18 | P0 | VII | No se encontró control atómico de capacidad en checkin/event-service (solo social usa `$inc`). | Operación condicional atómica + test de concurrencia. |
| O-19 | P1 | IV | 1–2 tests por servicio; sin Testcontainers, JaCoCo ni ArchUnit; moderation y recommendation sin tests ni CI; `integration-tests` y `load-tests` no corren en CI. | Umbrales del Principio IV aplicados en CI. |
| O-20 | P1 | IV | Flutter incluye `mock_api_service.dart` en `features/home`. | Pantallas de producción conectadas al backend real. |
| O-21 | P1 | X | Spring Boot 3.2.1 y Java 17 en CI; la línea 3.2 ya no recibe soporte OSS. | Spring Boot en versión soportada, Java 21. |
| O-22 | P1 | III/IX | Redis sin `requirepass` efectivo en compose (el `redis.conf` montado no se usa en `command`); Mongo y Kafka exponen puertos en el host. | Auth y red privada en todos los datastores. |
| O-23 | P2 | II | `JwtAuthenticationFilter` duplicado en 7 servicios; `backend/java/` sin uso aparente. | Librería compartida; directorios huérfanos eliminados. |
| O-24 | P1 | — | `CONTRIBUTING.md` exige `develop` pero los PR recientes van a `main`. | Protección de rama activa con CODEOWNERS y flujo de ramas acordado. |
| O-25 | P2 | IX | Documentos binarios (`*.docx`) versionados en la raíz; sin política de datos personales ni retención. | Documentos movidos a `docs/` o fuera del repo; política publicada. |

## Flujo de Desarrollo SDD y Puertas de Calidad

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
- Revisión aprobada por CODEOWNERS; `docs/api/**`, `docs/decisions/**`, infra y CI requieren
  ambos leads.

**Definición de Done:** la de `CONTRIBUTING.md` §5 más: métricas y trazas visibles en
staging, manifiestos/overlays actualizados, y escenario k6 en verde si toca un SLO.

**Production Readiness Review (antes del primer release y de cada servicio nuevo):**
todas las observaciones P0 cerradas, SLO medidos en staging, backup restaurado con éxito,
runbook de incidentes por servicio en `docs/runbooks/`, alertas enrutadas a un responsable
de guardia.

## Governance

- Esta constitución prevalece sobre cualquier otra práctica, plantilla o instrucción de
  agente del repositorio. En caso de conflicto: constitución > ADR > `CONVENTIONS.md` >
  `CONTRIBUTING.md` > `AGENTS.md` > código existente.
- **Enmiendas:** se proponen por PR a `.specify/memory/constitution.md` en rama
  `docs/constitution-<tema>`, con el Sync Impact Report actualizado; requieren aprobación de
  ambos leads (frontend y backend) y, si afecta infraestructura, del rol DevOps.
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

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE): pendiente de aprobación por ambos leads | **Last Amended**: 2026-10-12
