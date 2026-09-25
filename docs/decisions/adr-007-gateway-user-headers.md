# ADR-007: Headers `X-User-*` inyectados por el gateway

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), aprobada por ambos leads

## Contexto

`chat-service` y `media-service` obtienen la identidad del usuario con `@RequestHeader("X-User-Id")` **obligatorio** (si falta → 400). Además:

- Ambos están en `permitAll()` a nivel de servicio: **no validan JWT localmente**.
- Los inyecta `gateway/security/JwtAuthenticationFilter.java` (L60–62) tras verificar el token.
- **Esto no está documentado en ningún sitio** de `docs/`: un llamador que pruebe `http://localhost:8088/...` directamente recibirá 400 y no entenderá por qué.

## Decisión

Los headers de contexto son parte del contrato:

| Header | Origen | Ejemplo |
|--------|--------|---------|
| `X-User-Id` | claim `sub` del JWT | `usr_01H8...` |
| `X-User-Email` | claim `email` | `alex@vito.com` |
| `X-User-Roles` | claim `roles` | `USER,ORGANIZER` |

Reglas:

1. **Solo el gateway los añade.** El gateway los **elimina** de cualquier request entrante antes de reenviar (prevención de header spoofing).
2. Los servicios **nunca** los aceptan del cliente final; si llegan de fuera del gateway, se ignoran.
3. Un servicio puede elegir validar el JWT localmente además (`authenticated()`), pero **no puede** depender de un header `X-User-*` sin que el gateway lo garantice.
4. Las llamadas directas a un servicio (tests de integración, scripts, `curl`) deben simularlos explícitamente.

## Consecuencias

- Se documenta en `CONVENTIONS.md` §2.2 y en el handoff de gateway.
- `chat-service` y `media-service` quedan marcados en el handoff como **"solo operativos vía gateway"**.
- Si en el futuro un servicio necesita identidad confiable en flujo interno (Kafka), usará el `sub` del JWT propagado, no el header.
- `openapi.yaml` no expone estos headers (son infraestructura, no contrato de aplicación), pero el handoff del gateway sí.

## Alternativas consideradas

- **Cada servicio valida su propio JWT:** más seguro en teoría, implica compartir clave/pública de firmado entre 10 servicios y duplicar filtros. Se descarta para MVP; la validación centralizada en el gateway es el patrón ya elegido en `docs/architecture.md`.
- **Claim `userId` en la ruta o el body:** propenso a spoofing si el servicio no valida; además requiere cambiar 11 endpoints de chat y 8 de media.
