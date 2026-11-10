# Research: Ola 2a — grafo social y búsqueda de usuarios

**Fecha**: 2026-11-09 · **Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

Cada decisión cita el código revisado en `develop` (`3f41c6e` + #280) para que el plan no dependa
de suposiciones.

## Estado actual (lo que hay en el código)

| Pieza | Estado |
|-------|--------|
| `social-service` `SocialController` | 7 rutas sin contrato: follow, unfollow, profile, followers, following, `search` (stub que devuelve `[]`) e `is-following`. |
| `user_profiles` (`social-service`) | Nadie la llena. `getSocialProfile` responde 404 a cualquier usuario; follow y unfollow intentan `$inc` sobre documentos que no existen. |
| `follow_relationships` | Sin índices declarados. `follow` hace `exists` + `save` (no atómico: dos peticiones simultáneas crean la relación dos veces). Repetir el follow responde 409. |
| `getFollowers` / `getFollowing` | `findFollowerIdsByFollowingId` trae **todas** las relaciones, carga los perfiles y pagina en memoria con `skip`/`limit`. |
| `SocialProfile` | `boolean isFollowing` / `isFollowedBy` con Lombok: Jackson los serializa como `following` / `followedBy` (defecto de T-B13). |
| `KafkaEventPublisher` (social) | `USER_FOLLOWED` / `USER_UNFOLLOWED` en `social-events`, "dispara y olvida", timestamp en epoch, sin sobre. Ningún consumidor. |
| `auth-service` `User` | Colección `users`; `username` y `email` con índice único; `profile.displayName`, `profile.avatar`; `settings.privateProfile` (por defecto `false`, sin pantalla para cambiarlo). Sin rutas `/api/v1/users/**`. |
| Gateway | Rutas por prefijo (`/api/v1/auth/**` → auth 10/20; `/api/v1/social/**` → social 10/20). `userKeyResolver` usa el usuario autenticado. No hay ruta `/api/v1/users/**`. |
| Llamadas entre servicios | Un precedente: `mosaic-service` → `event-service` con `RestClient` y el mismo Bearer de la petición (`EventServiceOrganizerAdapter`). |
| Pruebas | `social-service`: 4 clases unitarias, sin Testcontainers, sin ArchUnit ni JaCoCo. `auth-service` ya tiene Testcontainers. |
| App | `social_remote_datasource.dart` llama las 7 rutas viejas, incluidas `/social/search` e `/social/is-following/{id}`. |

## R1 — Consulta por lotes de identidad (`getUsersByIds`)

- **Decisión:** `GET /api/v1/users?ids=<id>,<id>,…` en `auth-service`. Entre 1 y 50 ids, sin
  repetir (los repetidos se ignoran). Responde un array plano de `UserSummary`
  (`userId`, `username`, `displayName`, `avatar`) en el **orden en que se pidieron**. Los ids
  que no existen **se omiten** (no hay 404). Incluye a los perfiles privados: la privacidad solo
  aplica a la búsqueda (FR-004). Requiere sesión.
- **Quién la llama:** `social-service` en cada respuesta de perfil y listas (FR-012), por un
  puerto de salida `UserDirectory` con un adaptador `RestClient` que reenvía el Bearer de la
  petición en curso, igual que `EventServiceOrganizerAdapter`. Va directo a `auth-service` por
  el DNS de Compose (`AUTH_SERVICE_URI`), no por el gateway. La app también puede usarla.
- **Por qué:** decisión de los leads (Clarifications 2026-11-09). Sin copia ni Kafka no depende
  de T-C05 y los datos siempre están al día. Al estar en el contrato, cumple el Principio I y el II
  ("integración vía API contractualizada").
- **Alternativas:** `POST /users/batch` con cuerpo (descartado: es una lectura y 50 ids caben en
  la URL); réplica por eventos (descartada por los leads); que la app complete los datos (dos
  llamadas por pantalla).

## R2 — Fallos de `auth-service` al completar datos (FR-012a)

- **Decisión:** el `RestClient` de `social-service` usa un timeout de conexión de 1 s y de
  lectura de 2 s. Si `auth-service` no responde, da 5xx o el timeout vence, `social-service`
  responde `503 SERVICE_UNAVAILABLE` con el envelope ADR-003 y `Retry-After: 5`. Un `401` de
  `auth-service` se propaga como `401` (el token venció entre el gateway y aquí). No se reintenta
  dentro de la petición: el usuario reintenta.
- **Por qué:** nunca 500 ni datos inventados (FR-012a). Sin breaker propio: el del gateway ya
  protege la ruta y el tráfico entre servicios es bajo en la Fase Tracción.
- **Alternativa:** devolver la página sin nombres. Descartada: la app mostraría filas vacías.

## R3 — Búsqueda por prefijo sin tildes (`searchUsers`)

- **Decisión:** dos campos normalizados en `User`, que se escriben al registrar y en cualquier
  cambio de `username` o `displayName`:
  - `search.username`: `username` en minúsculas y sin tildes (NFD sin marcas diacríticas).
  - `search.terms`: las palabras de `displayName` normalizadas igual (array, sin vacíos).
  Índices: `{ "search.username": 1 }` y `{ "search.terms": 1 }`.
- **Consulta:** el texto se normaliza igual, se quita una `@` inicial y se **escapa** antes de usarlo
  como prefijo anclado (`^texto`), para que `.*` o comillas sean literales. Una agregación filtra
  `$or` sobre los dos campos, excluye a quien busca (`_id ≠ X-User-Id`) y a
  `settings.privateProfile = true`, calcula un rango (0 = `username` exacto, 1 = prefijo de
  `username`, 2 = prefijo de nombre), ordena por `rango, search.username` y pagina en la base
  (`$skip` / `$limit`). El orden es estable porque `username` es único.
- **Por qué:** los prefijos anclados usan el índice; la normalización resuelve "jose" → "José" sin
  depender de una collation que el `$regex` no respeta. Escala de sobra para la Fase Tracción.
- **Alternativas:** índice de texto de Mongo (descartado: no hace prefijos y ordena por puntaje,
  no por las reglas de FR-003); Atlas Search (no aplica: Mongo autoalojado, ADR-011).

## R4 — Usuarios existentes (FR-007, SC-002)

- **Decisión:** migración de una sola vez en `auth-service` (`Ola2aSearchMigration`), con el mismo
  patrón que `Ola1DataMigration`: registro en la colección `migrations`, pasos idempotentes, logs
  solo con conteos. Llena `search.*` en todos los usuarios y luego crea los índices. Corre al
  arrancar, antes de servir tráfico.
- **En `social-service`** (`Ola2aSocialMigration`): elimina relaciones duplicadas y las de alguien
  consigo mismo, convierte `createdAt` a fecha UTC si viniera como texto y después crea los índices
  de R5 y R6.

## R5 — Seguir y dejar de seguir: idempotencia y concurrencia (FR-009, FR-011)

- **Decisión:** índice único `{ followerId: 1, followingId: 1 }` en `follow_relationships`.
  - `follow`: valida que no sea uno mismo (`400 VALIDATION_ERROR`) y que el destino exista
    (`UserDirectory` con un solo id; si no vuelve, `404 NOT_FOUND`), e **inserta**. Un
    `DuplicateKeyException` significa "ya lo sigue": no es error y no publica evento.
    Responde `204`.
  - `unfollow`: valida el destino igual y **borra**. Si no borró nada, no es error y no publica
    evento. Responde `204`.
- **Por qué:** el índice único hace el follow atómico sin transacciones; la idempotencia evita los
  409 del doble toque (gana el contrato, ADR-010).
- **Alternativa:** `exists` + `save` (lo actual): carrera entre peticiones simultáneas.

## R6 — Contadores (FR-011, SC-004)

- **Decisión:** los contadores **se calculan** al leer con `count` sobre los índices de R7
  (`followingId` para seguidores, `followerId` para seguidos). Se dejan de guardar.
- **Por qué:** coinciden con las relaciones por construcción, también con concurrencia, y nunca son
  negativos. Un `count` sobre un prefijo de índice es barato a la escala de la Fase Tracción (miles
  de relaciones por usuario como mucho).
- **Revisión:** si un perfil supera ~10 000 seguidores o el p95 del perfil pasa de 300 ms, se vuelve
  a contadores guardados con `$inc` en la misma operación que el insert/delete. Se registra como
  riesgo, no como tarea.
- **Alternativa:** `$inc` sobre `user_profiles` (lo actual): deriva con fallos parciales y hoy apunta
  a documentos que no existen.

## R7 — Paginación de seguidores y seguidos (FR-013, SC-005)

- **Decisión:** índices `{ followingId: 1, createdAt: -1, _id: -1 }` y
  `{ followerId: 1, createdAt: -1, _id: -1 }`. Orden `createdAt` descendente con `_id` descendente
  de desempate; `skip`/`limit` en la base. `page` base 0; `size` por defecto 20, **máximo 50**.
  `createdAt` como `Instant` (lección de Bug-B01).
- **Por qué el máximo de 50:** cada página dispara una consulta por lotes a `auth-service` con un id
  por fila (R1). CONVENTIONS §5 fija 100 como techo general; 50 queda por debajo y se documenta en
  las dos operaciones.
- **Usuario inexistente:** antes de listar se comprueba el usuario con `UserDirectory`; si no existe,
  `404`. Un id de la página que `auth-service` ya no conoce se omite (FR-012a): la página puede traer
  menos filas que `size`, y la app sigue pidiendo hasta recibir una página vacía.

## R8 — Respuesta del perfil y T-B13 (FR-010)

- **Decisión:** el controlador responde un DTO propio (`SocialProfileResponse`) y deja de serializar
  el modelo de dominio. Campos: `userId`, `username`, `displayName`, `avatar`, `followersCount`,
  `followingCount`, `isFollowing`, `isFollowedBy`. Los booleanos se nombran con
  `@JsonProperty("isFollowing")` (o un `record` con ese componente) y lo cubre una prueba de
  serialización contra el fixture. En el propio perfil los dos van en `false`.
- **Fuera:** `bio` y `coverImage` no entran (no son de esta ola y viven en `auth-service`).
- **Retirado:** `GET /social/is-following/{userId}` (FUSIONAR) y el puerto `UserProfileRepository`. La
  colección `user_profiles` deja de usarse; no se borra en esta ola (Principio IX: borrar datos
  necesita su propia tarea y respaldo).

## R9 — Rutas y límites del gateway (FR-006)

- **Decisión:**
  - Ruta `users-search`: `Path=/api/v1/users/search`, `Method=GET` → `auth-service`, con
    `RequestRateLimiter` por usuario (`userKeyResolver`) con `replenishRate: 1` y
    `burstCapacity: 10`, más el `Retry-After` de `RateLimitedResponseFilter` (1 s).
  - Ruta `users`: `Path=/api/v1/users/**` → `auth-service`, con el límite de `auth` (10/20).
  - CONVENTIONS §9: excepción de la búsqueda, como la vista previa del enlace.
  - Las dos rutas exigen sesión (no van a `PUBLIC_PATHS`).
- `auth-service`: `SecurityConfig` exige autenticación en `/api/v1/users/**`; el `X-User-Id` del
  gateway identifica a quien busca.

## R10 — Eventos Kafka de social (FR-016)

- **Decisión:** se mantienen `USER_FOLLOWED` y `USER_UNFOLLOWED` en `social-events`, solo cuando la
  relación cambió de verdad (R5), y se deja de "disparar y olvidar": el `send` registra el
  resultado con `whenComplete`, como en la Ola 1 (R12 de la spec 003). Sobre versionado, outbox y
  declaración en `asyncapi.yaml` quedan para la tarea transversal de T-C05/T-B36; no hay
  consumidores (Complexity Tracking).

## R11 — Pruebas

- **`auth-service`:** búsqueda con Testcontainers MongoDB (tildes, @, exacto antes que prefijo,
  privados fuera, quien busca fuera, caracteres especiales literales, paginación estable),
  migración idempotente y `getUsersByIds` (orden, omitidos, máximo 50).
- **`social-service`:** agregar Testcontainers MongoDB. Concurrencia del follow (N hilos sobre el
  mismo par → una relación), idempotencia, contadores tras ráfagas (SC-004), paginación de 500
  seguidores sin repetidos (SC-005), `UserDirectory` simulado con `MockRestServiceServer` (éxito,
  omitidos, timeout → 503) y serialización de `isFollowing` contra el fixture.
- **Gateway:** prueba de la ruta `users-search` con su límite y `429` + `Retry-After`.
- **Contrato:** `npm run contract` (Spectral + fixtures) y pruebas Dart contra los fixtures nuevos.
- **Privacidad (SC-006):** una prueba recorre los fixtures y las respuestas de los tests de
  controlador y falla si aparece `email`, `phone` o un `@` con dominio.
- Docker apagado = pruebas saltadas: revisar `skipped="0"` en los reportes.

## R12 — App

- **Decisión:** datasource de usuarios (`GET /users/search`, `GET /users?ids=`) y ajuste del de
  social: sin `/social/search` ni `/social/is-following`; follow y unfollow tratan `204` como éxito;
  modelo `SocialProfile` con `isFollowing` / `isFollowedBy`. Pantallas: "¿A quién invitamos?" (quita
  el aviso de Bug-F08), "Mi Gente" sin `MockApiService` (parte social de T-F02) y perfil de otra
  persona con seguir / dejar de seguir y sus listas.
