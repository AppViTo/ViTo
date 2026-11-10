# Operaciones de la Ola 2a (insumo de `openapi.yaml`)

> **No es el contrato.** Es la propuesta que el agente de Alex lleva a `docs/api/openapi.yaml`
> en la rama `contract/ola-2a`, con D-08 de la Ola 2a y 1 CODEOWNER (Principio I). Si algo de
> aquí contradice al contrato publicado, gana el contrato.

Todas requieren `Authorization: Bearer` (`401 UNAUTHORIZED` sin sesión), responden errores con el
envelope ADR-003 y las listas son array plano (ADR-002). Fixtures en `docs/api/fixtures/users/` y
`docs/api/fixtures/social/`.

| # | operationId | Método y ruta | Dueño | Éxito | Errores |
|---|-------------|---------------|-------|-------|---------|
| 1 | `searchUsers` | `GET /api/v1/users/search?query=&page=&size=` | auth | `200` `UserSummary[]` (vacío si no hay coincidencias) | 400, 401, 429 |
| 2 | `getUsersByIds` | `GET /api/v1/users?ids=a,b,c` | auth | `200` `UserSummary[]` en el orden pedido; omite los que no existen | 400, 401 |
| 3 | `followUser` | `POST /api/v1/social/follow/{userId}` | social | `204` (también si ya lo seguía) | 400 (a uno mismo), 401, 404, 503 |
| 4 | `unfollowUser` | `DELETE /api/v1/social/follow/{userId}` | social | `204` (también si no lo seguía) | 401, 404, 503 |
| 5 | `getSocialProfile` | `GET /api/v1/social/profile/{userId}` | social | `200` `SocialProfile` | 401, 404, 503 |
| 6 | `listFollowers` | `GET /api/v1/social/followers/{userId}?page=&size=` | social | `200` `UserSummary[]` | 400, 401, 404, 503 |
| 7 | `listFollowing` | `GET /api/v1/social/following/{userId}?page=&size=` | social | `200` `UserSummary[]` | 400, 401, 404, 503 |

**Se retiran del código** (nunca estuvieron en el contrato): `GET /api/v1/social/search` y
`GET /api/v1/social/is-following/{userId}`.

## Parámetros

| Operación | Parámetro | Regla |
|-----------|-----------|-------|
| `searchUsers` | `query` | Obligatorio, 2–50 caracteres tras quitar una `@` inicial; texto literal |
| `searchUsers` | `page`, `size` | `page` ≥ 0 (0); `size` 1–20 (20) |
| `getUsersByIds` | `ids` | Obligatorio; 1–50 ids separados por coma; repetidos se ignoran |
| `listFollowers`, `listFollowing` | `page`, `size` | `page` ≥ 0 (0); `size` 1–50 (20). Máximo menor que el 100 de CONVENTIONS §5 (research R7) |

## Schemas

```yaml
UserSummary:
  type: object
  required: [userId, username, displayName, avatar]
  description: Datos públicos de un usuario. Nunca correo ni teléfono.
  properties:
    userId:      { type: string, example: usr_01H8X2K4QV }
    username:    { type: string, example: caro.mz }
    displayName: { type: [string, 'null'], example: Carolina Martínez }
    avatar:      { type: [string, 'null'], format: uri, example: null }

SocialProfile:
  type: object
  required: [userId, username, displayName, avatar, followersCount, followingCount, isFollowing, isFollowedBy]
  properties:
    userId:         { type: string }
    username:       { type: string }
    displayName:    { type: [string, 'null'] }
    avatar:         { type: [string, 'null'], format: uri }
    followersCount: { type: integer, minimum: 0 }
    followingCount: { type: integer, minimum: 0 }
    isFollowing:    { type: boolean, description: "Quien consulta sigue a este usuario. false en el propio perfil." }
    isFollowedBy:   { type: boolean, description: "Este usuario sigue a quien consulta. false en el propio perfil." }
```

## Reglas de `searchUsers` (descripción de la operación)

- Coincide por prefijo de `username` o de cualquier palabra de `displayName`, sin distinguir
  mayúsculas ni tildes.
- Orden: `username` exacto, prefijo de `username`, prefijo de nombre; a igual rango, `username`
  ascendente. Estable entre páginas.
- Nunca busca por correo ni lo devuelve; no incluye a quien busca ni a perfiles privados.
- Límite por usuario: 1 por segundo con ráfaga de 10; `429` con `Retry-After: 1`
  (excepción en CONVENTIONS §9).

## Fixtures propuestos

| Carpeta | Archivos |
|---------|----------|
| `users/` | `searchUsers.200.json`, `searchUsers.empty.200.json`, `searchUsers.400.json`, `searchUsers.401.json`, `searchUsers.429.json`, `getUsersByIds.200.json`, `getUsersByIds.400.json` |
| `social/` | `followUser.204` (sin cuerpo; solo request), `followUser.self.400.json`, `followUser.404.json`, `followUser.503.json`, `unfollowUser.404.json`, `getSocialProfile.200.json`, `getSocialProfile.own.200.json`, `getSocialProfile.404.json`, `listFollowers.200.json`, `listFollowers.empty.200.json`, `listFollowers.400.json`, `listFollowing.200.json` |

## Cambios fuera de `openapi.yaml`

- `CONVENTIONS.md` §9: excepción del límite de `searchUsers`.
- `endpoint-inventory.md`: `social/search` → `auth-service` `GET /users/search`; nueva fila
  `getUsersByIds`; `is-following` marcada como fusionada; las 5 rutas de social, `CONTRATO`.
- ADR-016: nota en la fila de identidad: la búsqueda y los datos públicos por lotes son de
  `auth-service`; `social-service` es dueño solo del grafo.
