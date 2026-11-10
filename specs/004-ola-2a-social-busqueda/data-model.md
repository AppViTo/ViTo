# Data Model: Ola 2a — grafo social y búsqueda de usuarios

**Spec**: [spec.md](./spec.md) · **Research**: [research.md](./research.md)

Cada servicio es dueño de su base (Principio II): `auth-service` → `vito_auth`,
`social-service` → `vito_social`. Ninguno lee la del otro.

## `auth-service` · colección `users` (existente)

Campos que ya existen y que usa esta ola:

| Campo | Tipo | Uso en la ola |
|-------|------|---------------|
| `_id` | string | `userId` en todas las respuestas |
| `username` | string, único | Búsqueda y `UserSummary` |
| `profile.displayName` | string, opcional | Búsqueda y `UserSummary` |
| `profile.avatar` | string (URI), opcional | `UserSummary` |
| `settings.privateProfile` | boolean, por defecto `false` | Excluye de la búsqueda (FR-004) |
| `email`, `phone` | — | **Nunca** salen en esta ola (FR-005, SC-006) |

Campos nuevos (R3):

| Campo | Tipo | Regla |
|-------|------|-------|
| `search.username` | string | `username` en minúsculas y sin tildes. Se escribe al registrar y al cambiar `username`. |
| `search.terms` | array de string | Palabras de `displayName` en minúsculas y sin tildes, sin vacíos. Vacío si no hay nombre. |

Índices nuevos: `{ "search.username": 1 }` y `{ "search.terms": 1 }`. Se crean después de la
migración `Ola2aSearchMigration` (R4).

### Vista `UserSummary` (no se guarda)

`{ userId, username, displayName | null, avatar | null }`. La devuelven `searchUsers` y
`getUsersByIds`. Es la misma forma que `UserSearchResult` del RFC #277; el contrato usa un solo
schema para las dos.

## `social-service` · colección `follow_relationships` (existente)

| Campo | Tipo | Regla |
|-------|------|-------|
| `_id` | ObjectId | Desempate del orden (R7) |
| `followerId` | string | Quien sigue. Distinto de `followingId`. |
| `followingId` | string | A quien sigue |
| `createdAt` | `Instant` (fecha UTC) | Cuándo empezó a seguirlo. Orden de las listas. |
| `notificationsEnabled` | boolean | Se conserva; no se expone en esta ola |

Índices (R5, R7):

| Índice | Para |
|--------|------|
| `{ followerId: 1, followingId: 1 }` **único** | Una sola relación por par; follow atómico e idempotente |
| `{ followingId: 1, createdAt: -1, _id: -1 }` | Seguidores de X, paginados, y su contador |
| `{ followerId: 1, createdAt: -1, _id: -1 }` | A quién sigue X, paginado, y su contador |

Migración `Ola2aSocialMigration` (R4), antes de crear los índices: borra duplicados (deja la
relación más antigua) y relaciones de alguien consigo mismo, y convierte `createdAt` de texto a
fecha UTC. Registro en `migrations`, logs solo con conteos.

### Transiciones

```text
(sin relación) --follow--> (relación)      # inserta; publica USER_FOLLOWED
(relación)     --follow--> (relación)      # no-op (clave duplicada); sin evento
(relación)     --unfollow--> (sin relación) # borra; publica USER_UNFOLLOWED
(sin relación) --unfollow--> (sin relación) # no-op; sin evento
```

### Vista `SocialProfile` (no se guarda)

| Campo | Origen |
|-------|--------|
| `userId`, `username`, `displayName`, `avatar` | `auth-service` por lotes (R1) |
| `followersCount` | `count({followingId: userId})` (R6) |
| `followingCount` | `count({followerId: userId})` (R6) |
| `isFollowing` | existe `{followerId: yo, followingId: userId}`; `false` si `userId` = yo |
| `isFollowedBy` | existe `{followerId: userId, followingId: yo}`; `false` si `userId` = yo |

### Listas de seguidores y seguidos

Array plano de `UserSummary` en el orden de `createdAt` descendente de la relación. Un id que
`auth-service` no devuelve se omite (FR-012a).

## `social-service` · colección `user_profiles` (se deja de usar)

Ningún código la lee ni la escribe tras esta ola (R8). Sus documentos no se borran aquí; borrarla
es una tarea aparte con respaldo (Principio IX).

## Validaciones (resumen)

| Regla | Error |
|-------|-------|
| `query` de búsqueda con 2 a 50 caracteres (tras quitar la @ inicial) | `400 VALIDATION_ERROR` |
| `size` de la búsqueda de 1 a 20; de las listas de 1 a 50; `page` ≥ 0 | `400 VALIDATION_ERROR` |
| `ids` de `getUsersByIds` de 1 a 50 | `400 VALIDATION_ERROR` |
| Seguirse a uno mismo | `400 VALIDATION_ERROR` |
| Usuario objetivo inexistente (follow, unfollow, perfil, listas) | `404 NOT_FOUND` |
| `auth-service` no disponible al completar datos | `503 SERVICE_UNAVAILABLE` + `Retry-After` |
| Búsqueda por encima del límite por usuario | `429 RATE_LIMITED` + `Retry-After` |
