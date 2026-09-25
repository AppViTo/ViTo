# ADR-009: `409 Conflict` para duplicados, `400 Bad Request` para validación

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), aprobada por ambos leads

## Contexto

`openapi.yaml` documenta `409` para usuario duplicado en `POST /auth/register`; el backend real devuelve **`400`** porque `GlobalExceptionHandler` captura `IllegalArgumentException` y lo traduce a `BAD_REQUEST`. El resultado: `schemathesis` fallaría y el frontend no puede distinguir "email ya registrado" de "campo inválido" por código HTTP.

## Decisión

| Situación | Código | `error` |
|-----------|--------|---------|
| Campo obligatorio ausente o con formato inválido | `400` | `VALIDATION_ERROR` |
| Regla de negocio violada (ej. cupo lleno, evento ya cancelado) | `409` | `CONFLICT` |
| Unicidad violada (email/username ya registrado) | **`409`** | `CONFLICT` |
| Credenciales incorrectas | `401` | `UNAUTHORIZED` |
| Token ausente/expirado | `401` | `UNAUTHORIZED` |
| Sin permiso sobre el recurso | `403` | `FORBIDDEN` |
| Recurso inexistente | `404` | `NOT_FOUND` |

Distinción clave: **`400` = tu request está mal formado. `409` = tu request es válido pero entra en conflicto con el estado actual del sistema.**

`message` (ADR-003) siempre explica el caso en es-ES; `details` opcional señala el campo.

## Consecuencias

- `auth-service` debe devolver `409` en registro duplicado (añadir `@ResponseStatus(CONFLICT)` o excepción específica `DuplicateUserException`).
- `openapi.yaml` documenta `409` con `ErrorResponse` en todas las operaciones con unicidad o reglas de estado.
- El frontend puede mostrar un mensaje específico ("ese email ya está registrado") usando el código, sin parsear strings.

## Alternativas consideradas

- **`200` con `{duplicated: true}`:** descartado, pierde la semántica HTTP y obliga a lógica especial en el cliente.
- **Mantener `400` para todo:** más simple en backend, pero el contrato queda ambiguo y el cliente no puede ramificar.
