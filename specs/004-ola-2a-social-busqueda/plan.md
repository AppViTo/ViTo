# Implementation Plan: Ola 2a del contrato — grafo social y búsqueda de usuarios

**Branch**: `contract/ola-2a-spec` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-ola-2a-social-busqueda/spec.md`

## Summary

Publicar en el contrato y luego implementar 7 operaciones: búsqueda de usuarios y datos públicos
por lotes en `auth-service` (`searchUsers`, `getUsersByIds`), y seguir, dejar de seguir, perfil
social con `isFollowing`/`isFollowedBy`, seguidores y seguidos en `social-service`.
`social-service` pasa a guardar solo el grafo: los contadores se calculan con índices y el
nombre y el avatar se piden a `auth-service` por lotes en cada respuesta, sin copia ni Kafka.
Por eso la ola no depende de T-C05. Seguir es atómico e idempotente gracias a un índice único.
Se retiran `/social/search` (stub) e `/social/is-following`. La app usa la búsqueda real en
"¿A quién invitamos?" (Bug-F08) y deja los datos falsos en "Mi Gente" (T-F02 social). Ver
[research.md](./research.md).

## Technical Context

**Language/Version**: Java 21 + Spring Boot 4.0.9 (ADR-018) en `auth-service`,
`social-service` y `api-gateway` (Spring Cloud Gateway 5); Dart/Flutter en `frontend/vito_app`.

**Primary Dependencies**: Spring Data MongoDB, Spring Web (`RestClient` para la llamada
`social` → `auth`), Spring Kafka (eventos existentes de social), Spring Security; gateway con
`RequestRateLimiter` sobre Redis; Flutter BLoC, `get_it` y Dio.

**Storage**: MongoDB de `auth-service` (`users`, 2 campos y 2 índices nuevos) y de
`social-service` (`follow_relationships`, 3 índices nuevos; `user_profiles` sin uso). Redis solo
para el rate limit del gateway.

**Testing**: JUnit 5 + Testcontainers MongoDB (nuevo en `social-service`; ya existe en
`auth-service`), `MockRestServiceServer` para la llamada a `auth-service`, pruebas de controlador
contra los fixtures, `npm run contract` (Spectral + fixtures) y `flutter test` (contrato Dart y blocs).

**Target Platform**: VPS con Docker Compose (Fase Tracción, ADR-011); app web en Cloudflare Pages;
Android/iOS.

**Project Type**: web-service (microservicios) + app móvil/web.

**Performance Goals**: búsqueda < 1 s p95 (SC-003); perfil y listas < 300 ms p95 vía gateway, con
una sola llamada por lotes a `auth-service` por respuesta.

**Constraints**: nunca correo ni teléfono en una respuesta (SC-006); sin leer la base de otro
servicio (Principio II); contadores exactos con concurrencia (SC-004); texto de búsqueda literal;
`503` con envelope si `auth-service` no responde.

**Scale/Scope**: 7 operaciones (2 en `auth-service`, 5 en `social-service`), 2 rutas retiradas,
2 rutas nuevas en el gateway, 2 migraciones de datos, ~19 fixtures y 3 pantallas en la app.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cumplimiento | Estado |
|-----------|--------------|--------|
| I. Contrato primero | Nada se implementa antes del PR `contract/ola-2a` con D-08 de la Ola 2a y 1 CODEOWNER. Cada operación lleva `operationId`, ejemplos, errores y fixtures ([contrato propuesto](./contracts/ola-2a-operations.md)). Las dos rutas fuera de contrato se retiran del código. La llamada `social` → `auth` usa una operación del contrato (`getUsersByIds`). Sin `schemathesis` (ver Complexity Tracking) | ⚠️ |
| II. Hexagonal y servicios autónomos | `social-service` deja de guardar identidad y la obtiene por un puerto de salida (`UserDirectory`) con un adaptador HTTP; cada servicio solo toca su base. Sin ArchUnit en `social-service` (ver Complexity Tracking) | ⚠️ |
| III. Seguridad por defecto | Las 7 operaciones exigen sesión; la llamada interna reenvía el Bearer del usuario (no hay credencial de servicio); límite por usuario en la búsqueda; consulta escapada (sin inyección de `$regex`); `X-User-Id` del gateway para excluir a quien busca | ✅ |
| IV. Calidad verificable | TDD en la búsqueda (rangos, tildes, privados) y en el follow concurrente; Testcontainers en los dos servicios; pruebas de contrato Java y Dart; prueba automática de "sin correo ni teléfono". Cobertura ≥ 80 % del código nuevo medida a mano (sin JaCoCo en CI) | ⚠️ |
| V. Mensajería confiable | Se mantienen `USER_FOLLOWED`/`USER_UNFOLLOWED` sin sobre ni outbox; se publican solo cuando la relación cambia y se deja de "disparar y olvidar" (R10) | ⚠️ |
| VI. Observabilidad | Logs con conteos y `userId` técnico, nunca el texto buscado ni datos personales; métricas Micrometer del `RestClient` (latencia y errores hacia `auth-service`) y contador de `429` de `users-search` en el tablero del gateway (T-I13) | ✅ |
| VII. Rendimiento | Índices declarados para búsqueda, follow y listas; paginación en la base; una sola llamada por lotes por respuesta; tamaño de página acotado a 50 | ✅ |
| VIII. Entrega inmutable | Sin servicios nuevos; `AUTH_SERVICE_URI` por entorno (`.env.example`, `docker-compose*.yml`) | ✅ |
| IX. Privacidad | Búsqueda sin correo ni teléfono, sin privados y sin uno mismo; migraciones idempotentes con registro; `user_profiles` no se borra sin respaldo | ✅ |
| X. Simplicidad | Sin tecnologías nuevas; menos estado en `social-service` (sin copia de perfiles ni contadores guardados) | ✅ |

**Resultado del gate:** pasa con cuatro desviaciones justificadas en *Complexity Tracking*
(V, II, I/IV schemathesis, IV JaCoCo), las mismas de la Ola 1, que el PR de la spec debe aprobar
(Governance).

**Re-check tras la fase 1 (2026-11-09):** el diseño no agrega desviaciones. La dependencia de
`social-service` respecto de `auth-service` en tiempo de ejecución es nueva y se controla con
timeouts y `503` (R2). Si el p95 del perfil no se cumple, la alternativa registrada es la copia por
eventos (R1), que sí depende de T-C05.

## Project Structure

### Documentation (this feature)

```text
specs/004-ola-2a-social-busqueda/
├── plan.md                         # Este archivo
├── research.md                     # Fase 0: R1–R12
├── data-model.md                   # Fase 1: colecciones, índices, vistas y validaciones
├── quickstart.md                   # Fase 1: validación de extremo a extremo
├── contracts/ola-2a-operations.md  # Fase 1: insumo de openapi.yaml (no es el contrato)
├── checklists/requirements.md
└── tasks.md                        # Fase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
docs/api/
├── openapi.yaml                    # 7 operaciones, UserSummary, SocialProfile, tags Users/Social
├── CONVENTIONS.md                  # §9: excepción del límite de searchUsers
└── fixtures/users/, fixtures/social/
docs/roadmap/endpoint-inventory.md  # social/search → auth; getUsersByIds; is-following fusionada
docs/decisions/adr-016-*.md         # nota: búsqueda e identidad por lotes en auth-service

backend/auth-service/src/main/java/com/vito/auth/
├── domain/model/User.java          # search.username, search.terms
├── domain/port/inbound/            # UserDirectoryService: search, findByIds (nuevo)
├── domain/port/outbound/UserRepository.java     # search(...), findAllByIds(...)
├── application/service/            # normalización (sin tildes), reglas de rango
└── infrastructure/
    ├── adapter/inbound/rest/UserController.java # /api/v1/users, /api/v1/users/search (nuevo)
    ├── adapter/outbound/persistence/UserMongoAdapter.java  # agregación con rango, índices
    ├── migration/Ola2aSearchMigration.java      # llena search.* (nuevo)
    └── config/SecurityConfig.java               # /api/v1/users/** autenticado

backend/social-service/src/main/java/com/vito/social/
├── domain/model/                   # FollowRelationship (createdAt Instant); SocialProfile sin contadores guardados
├── domain/port/outbound/           # UserDirectory (nuevo); se retira UserProfileRepository
├── application/service/SocialServiceImpl.java   # follow/unfollow idempotentes, contadores por count
└── infrastructure/
    ├── adapter/inbound/rest/       # SocialController sin search ni is-following; SocialProfileResponse
    ├── adapter/outbound/directory/AuthUserDirectoryAdapter.java  # RestClient + Bearer (nuevo)
    ├── adapter/outbound/persistence/FollowMongoAdapter.java      # insert con clave única, paginación en la base
    ├── adapter/outbound/messaging/KafkaEventPublisher.java       # whenComplete
    └── migration/Ola2aSocialMigration.java      # duplicados, createdAt, índices (nuevo)
backend/social-service/src/test/    # Testcontainers MongoDB, concurrencia, contrato

backend/api-gateway/src/main/resources/application.yml  # rutas users-search (1/10) y users (10/20)

frontend/vito_app/lib/features/
├── social/data/                    # datasource sin /social/search ni is-following; SocialProfile con isFollowing
├── users/ (o social/) data + domain # searchUsers, getUsersByIds
├── events/presentation/            # "¿A quién invitamos?" con la búsqueda real
└── people/ o social/presentation/  # "Mi Gente" y perfil de otra persona sin MockApiService

.env.example, docker-compose*.yml   # AUTH_SERVICE_URI para social-service
```

**Structure Decision**: sin servicios nuevos (Principio X). `auth-service` es dueño de la identidad
y de la búsqueda (ADR-004, RFC #277); `social-service`, solo del grafo (ADR-016). El reparto de
tareas por dueño sigue los CODEOWNERS:

| Bloque | Dueño |
|--------|-------|
| Contrato (`docs/api/**`), fixtures, inventario y nota en ADR-016 | Agente de Alex (pista C) |
| App Flutter (`frontend/**`) | Agente de Alex (pista F) |
| `auth-service`, `social-service`, gateway y migraciones | Sesión de Bernardo (pista B) |
| `AUTH_SERVICE_URI` en `.env.example`, Compose y producción; métricas en Grafana | Sesión de Bernardo (pista I) |

**Orden:** contrato → (backend ∥ app contra el mock de Prism) → integración y E2E. Dentro del
backend, `auth-service` primero: `social-service` necesita `getUsersByIds`. La búsqueda (US1) puede
salir sola en un release, antes que el resto, porque cierra Bug-F08.

**Coordinación con PR abiertos:** #282 (T-B18, `shared-web` y reactor Maven) cambia el
`GlobalExceptionHandler` y el filtro JWT de los servicios. Si se mergea antes, las tareas de esta
ola usan `shared-web`; si no, se rebasan después.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| V: eventos `social-events` sin sobre versionado, sin outbox y sin declarar en `asyncapi.yaml` | No hay ningún consumidor; el outbox y la declaración son transversales a los 9 productores | Hacerlo aquí ata la ola a T-C05, justo lo que evita la decisión de FR-012. Se resuelve con T-C05/T-B36 antes del primer consumidor |
| II: sin ArchUnit en `social-service` | La verificación automática de capas es transversal a los 10 servicios | Agregarlo solo aquí deja la regla a medias; va con T-B18 (`backend/shared-web`) |
| I y IV: sin `schemathesis` contra el servicio | No existe ningún job de schemathesis ni entorno efímero (O-19); la ola cubre el contrato con Spectral, la validación de fixtures y pruebas de controlador contra los mismos fixtures | Montarlo solo para esta ola es infraestructura transversal; sigue como propuesta de tarea de CI (igual que en la Ola 1) |
| IV: sin umbral de cobertura (JaCoCo) en CI | JaCoCo no está configurado en ningún servicio (O-19) | Transversal, igual que ArchUnit. La ola exige pruebas por historia y TDD en la lógica crítica; el 80 % se mide a mano con `jacoco:report` local en cada PR de backend |
