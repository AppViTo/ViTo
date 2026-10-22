# Decisiones de arquitectura y contrato (ADR)

Registro de las decisiones que afectan el contrato compartido entre el **equipo frontend** y el **equipo backend**. Son vinculantes para ambos.

**Proceso:** una decisión nueva se propone por RFC (issue `contract-change`), se aprueba por al menos 1 lead (CODEOWNER), y se registra aquí en una rama `contract/<nombre>`.

**Decisiones abiertas:** las pendientes (D-01..D-08) están en [`docs/roadmap/decisions-pending.md`](../roadmap/decisions-pending.md). Al cerrarse, cada una se convierte en el siguiente ADR (011 en adelante) y se marca allí con su número.

| # | Título | Estado | Fecha |
|---|--------|--------|-------|
| [001](./adr-001-event-structure.md) | Estructura del recurso Event: `title` + `details` + `settings` + `stats` | Aceptada | 2026-09-25 |
| [002](./adr-002-list-envelope.md) | Las listas se devuelven como array plano | Aceptada | 2026-09-25 |
| [003](./adr-003-error-envelope.md) | Envelope de error único `{error,message,status,timestamp}` | Aceptada | 2026-09-25 |
| [004](./adr-004-auth-tokens.md) | `AuthResponse` con tokens y ciclo de refresh unificado | Aceptada | 2026-09-25 |
| [005](./adr-005-http-status-codes.md) | Códigos HTTP de creación, borrado y contenido | Aceptada | 2026-09-25 |
| [006](./adr-006-websocket-raw.md) | WebSocket crudo, no STOMP | Aceptada | 2026-09-25 |
| [007](./adr-007-gateway-user-headers.md) | Headers `X-User-*` inyectados por el gateway | Aceptada | 2026-09-25 |
| [008](./adr-008-pagination.md) | Paginación `page`/`size` sin `total` | Aceptada | 2026-09-25 |
| [009](./adr-009-conflict-vs-validation.md) | `409` para duplicados, `400` para validación | Aceptada | 2026-09-25 |
| [010](./adr-010-backend-is-reference.md) | El código backend existente es referencia, no fuente de verdad | Aceptada | 2026-09-25 |
| [011](./adr-011-traction-stack.md) | Stack de despliegue de la Fase Tracción (100.000 COP/mes) | Aceptada | 2026-10-17 |
| [012](./adr-012-branching-and-release.md) | Ramas, release semanal y promoción a producción | Aceptada | 2026-10-17 |
| [013](./adr-013-account-ownership.md) | Titularidad de cuentas, credenciales y medio de pago | Aceptada | 2026-10-17 |
| [014](./adr-014-events-listing-scope.md) | Listado de eventos con `GET /events?scope=mine\|upcoming` | Aceptada | 2026-10-19 |
| [015](./adr-015-chat-ws-identity.md) | Identidad en el WS de chat sin `?userId=` | Aceptada | 2026-10-20 |
| [017](./adr-017-shared-java-code.md) | Código Java compartido: copia temporal ahora, `shared-web` después | Aceptada | 2026-10-22 |

**Convención de formato:** estilo [MADR](https://adr.github.io/madr/) simplificado — Estado / Contexto / Decisión / Consecuencias / Alternativas.
