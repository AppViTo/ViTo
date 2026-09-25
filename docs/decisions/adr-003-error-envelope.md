# ADR-003: Envelope de error único

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), aprobada por ambos leads

## Contexto

Hoy coexisten **tres** formas de error distintas en el backend:

| Fuente | Forma |
|--------|-------|
| `docs/api/README.md` (documentación) | `{error, message, timestamp}` |
| Gateway `JwtAuthenticationFilter` | `{error, status}` — sin `timestamp` |
| `auth-service GlobalExceptionHandler` | `{timestamp, status, error, message, details?}` |
| `chat-service GlobalExceptionHandler` | `{error, message, timestamp}` |

El frontend lee exclusivamente `response.data['message']` en **todos** los datasources para mostrar el error al usuario. Si `message` no existe, el usuario ve "Unknown error".

El schema `ErrorResponse` de `openapi.yaml` existe pero **no está referenciado por ninguna operación**.

## Decisión

Envelope único para **toda** respuesta 4xx/5xx, en **todos** los servicios y en el gateway:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "El campo title es obligatorio",
  "status": 400,
  "timestamp": "2026-09-25T14:03:22Z",
  "details": { "title": "es obligatorio" }
}
```

| Campo | Obligatorio | Notas |
|-------|-------------|-------|
| `error` | sí | código `SCREAMING_SNAKE_CASE` para máquinas |
| `message` | **sí** | legible, es-ES; es lo que muestra el frontend |
| `status` | sí | espejo del status HTTP |
| `timestamp` | sí | ISO-8601 UTC terminado en `Z` (no `LocalDateTime` sin zona) |
| `details` | no | solo validación, mapa `campo → motivo` |

Códigos canónicos: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `INTERNAL_ERROR`, `SERVICE_UNAVAILABLE`.

## Consecuencias

- En `openapi.yaml`, toda respuesta no-2xx referencia `#/components/responses/...` que usa `ErrorResponse` (hoy todas son `description` sin `content`).
- El gateway y cada servicio usan el mismo `@ControllerAdvice` / manejador equivalente.
- `ErrorResponse` deja de ser schema huérfano.
- Una capa común de errores es candidata a un módulo Maven compartido en el backend.

## Alternativas consideradas

- **RFC 7807 `application/problem+json`:** estándar y elegante, pero exige `Content-Type` especial y parsers adicionales en Dart. No compensa para el MVP.
- **Adaptar el frontend a leer 3 formas distintas:** descartado, duplicaría lógica de manejo de errores en cada datasource.
