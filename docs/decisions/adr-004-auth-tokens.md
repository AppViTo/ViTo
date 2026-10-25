# ADR-004: `AuthResponse` con tokens y ciclo de refresh unificado

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), aprobada por ambos leads

## Contexto

Tres problemas encadenados:

1. **El frontend descarta los tokens.** `AuthRepositoryImpl.login/register` solo llama `saveUserId(...)`; `saveTokens(...)` solo se invoca desde el interceptor después de un refresh. Resultado: el header `Authorization` **nunca se envía** y ningún endpoint autenticado funciona.
2. **`/auth/refresh` tiene dos parsers incompatibles en el mismo cliente:**
   - `TokenInterceptor` espera `{accessToken, refreshToken}`
   - `auth_remote_datasource` espera `{user: {...}}`
3. **Divergencia de esquema:** el OpenAPI declara `tokenType` (que la clase Java `AuthResponse` no tiene) y un `user` con `profile`/`stats` anidados, mientras el controller real devuelve `UserInfo` plano `{id, username, email, displayName, avatar}`.

## Decisión

### Forma de la respuesta (`register`, `login`, `refresh`)

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "expiresIn": 900,
  "user": {
    "id": "usr_01H8...",
    "username": "alex_villa",
    "email": "alex@vito.com",
    "displayName": "Alex Villa",
    "avatar": "https://.../avatar.jpg"
  }
}
```

| Campo | Obligatorio | Notas |
|-------|-------------|-------|
| `accessToken` | sí | JWT, `Authorization: Bearer` |
| `refreshToken` | sí | opaque, se envía solo en el body de `/auth/refresh` |
| `expiresIn` | sí | segundos hasta expirar del access token |
| `user` | sí | **plano**: `id`, `username`, `email`, `displayName`, `avatar` |

- Se **elimina** `tokenType` (el cliente siempre asume `Bearer`).
- Se **elimina** `user.profile` / `user.stats` del auth. El perfil completo se obtiene con `GET /api/v1/social/profile/{userId}` cuando la pantalla lo necesita.

### Flujo

```
login/register
  → guardar accessToken + refreshToken (FlutterSecureStorage)
  → todo request sale con Authorization: Bearer

401 en cualquier request
  → POST /auth/refresh {refreshToken}
  → {accessToken, refreshToken}   ← MISMA forma para el datasource y el interceptor
  → guardar y reintentar el request original

refresh falla
  → limpiar tokens → redirigir a /login

logout
  → POST /auth/logout (con Bearer) → 204 → limpiar tokens locales
```

**Un solo parser.** `auth_remote_datasource` y `TokenInterceptor` leen exactamente los mismos campos.

## Consecuencias

- **Frontend (obligatorio, P0):** `AuthRepositoryImpl.login/register` debe llamar `saveTokens(accessToken, refreshToken)`; `AuthBloc` deja de estar mockeado y usa `LoginUseCase` + `GET /auth/me`.
- **Backend:** `AuthResponse.java` añade `expiresIn` si no existe y documenta `user` plano; `openapi.yaml` se corrige (quitar `tokenType`, aplanar `User`).
- `register` acepta además `displayName` (campo opcional).

## Alternativas consideradas

- **Devolver `user` con `profile`/`stats` anidados (OpenAPI actual):** acoplamiento innecesario entre auth y social; auth no debe conocer las estadísticas de seguidores.
- **Cookie httpOnly en vez de Bearer:** mejor seguridad teórica, pero el frontend ya está construido con interceptor Bearer y con WebSocket autenticado por query param.

## Actualizaciones

### 2026-10-18 — `openapi.yaml` alineado (T-C08, issue #41)

Se aplicaron las consecuencias de contrato que faltaban:

- `AuthResponse` sin `tokenType`, con `accessToken`, `refreshToken`, `expiresIn` y `user`
  obligatorios; `expiresIn` documentado en segundos.
- `user` es el nuevo schema **`AuthUser`** plano (`id`, `username`, `email` obligatorios;
  `displayName` y `avatar` opcionales y que pueden ser `null`). El schema `User` con
  `profile`/`stats` queda para social-service.
- `logoutUser` → `204` sin cuerpo.
- `RegisterRequest` acepta `displayName` opcional.

Fixtures `authentication/*` y tests Dart de contrato actualizados a esta forma.
