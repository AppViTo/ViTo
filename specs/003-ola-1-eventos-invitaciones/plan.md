# Implementation Plan: Ola 1 del contrato — eventos, invitaciones y RSVP

**Branch**: `contract/ola-1-spec` | **Date**: 2026-11-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-ola-1-eventos-invitaciones/spec.md`

## Summary

Publicar en el contrato y luego implementar 13 operaciones: editar (`PATCH`) y cancelar
(`DELETE` → `CANCELLED`) el evento, RSVP como única forma de responder (cupo atómico y lista
de espera con promoción automática), asistentes solo para el organizador con un contador de
confirmados para los demás, invitaciones por la app, enlaces de invitación compartibles con
vista previa pública, `scope=invited` y `GET /auth/me`. La implementación se concentra en
`event-service`, con cambios menores en `auth-service` (claim `name`), el gateway (ruta
pública por método y patrón exacto, límites de intentos) y la app Flutter (pantalla de
edición, enlace, ruta web `/i/{token}`, RSVP y contador). Ver [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21 + Spring Boot 4.0.9 (ADR-018) en `event-service`,
`auth-service` y `api-gateway` (Spring Cloud Gateway 5); Dart/Flutter en `frontend/vito_app`.

**Primary Dependencies**: Spring Data MongoDB, Spring Kafka, Spring Security, Spring Data
Redis (rate limit del gateway); Flutter BLoC, `get_it`, Dio, `share_plus`.

**Storage**: MongoDB de `event-service` (`events`, `invitations`, `rsvps` y la nueva
`invite_links`), con índices nuevos (data-model). Redis solo para el rate limit del gateway.

**Testing**: JUnit 5 + Testcontainers MongoDB (nuevo en `event-service`), tests de
controlador contra fixtures, `npm run contract` (Spectral + fixtures), `flutter test`
(contrato Dart y blocs), k6 en `backend/load-tests/`.

**Target Platform**: VPS con Docker Compose (Fase Tracción, ADR-011); app web en Cloudflare
Pages; Android/iOS.

**Project Type**: web-service (microservicios) + app móvil/web.

**Performance Goals**: lecturas < 300 ms p95 vía gateway; RSVP y unión por enlace < 1 s p95
(SC-008); eventos de 150 asistentes (Fase Tracción).

**Constraints**: cupo nunca superado con respuestas simultáneas (SC-003); vista previa sin
dirección ni coordenadas; token del enlace con 128 bits y fuera de los logs; mismo 404 para
cualquier enlace no vigente; sin leer bases de otros servicios.

**Scale/Scope**: 13 operaciones (12 en `event-service`, 1 en `auth-service`), 1 colección
nueva, 1 cabecera nueva (`X-User-Name`), ~25 fixtures, 4 pantallas o flujos en la app.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cumplimiento | Estado |
|-----------|--------------|--------|
| I. Contrato primero | Nada se implementa antes del PR de contrato (`contract/ola-1`) con D-08 de la Ola 1 y 1 CODEOWNER. Cada operación lleva `operationId`, ejemplos, errores y fixtures ([contrato propuesto](./contracts/ola-1-operations.md)). `invitations/respond` se retira del código | ✅ |
| II. Hexagonal y servicios autónomos | Todo en `event-service` por capas; nombres desnormalizados desde el token (R6), sin leer `vito_auth` ni `vito_social`. ArchUnit no existe en `event-service` (ver Complexity Tracking) | ⚠️ |
| III. Seguridad por defecto | Autorización por recurso en cada operación del organizador (corrige el hallazgo del #204); ruta pública exacta por método (R5); límites de intentos; 404 único; token aleatorio de 128 bits fuera de los logs; `INVITE_LINK_BASE_URL` obligatoria en `prod`; `X-User-Name` con la higiene de T-B01 | ✅ |
| IV. Calidad verificable | TDD en cupo, lista de espera, RSVP y enlace; Testcontainers MongoDB; pruebas de contrato Java y Dart; cobertura ≥ 80 % en `domain` + `application` del código nuevo | ✅ |
| V. Mensajería confiable | Se mantienen los eventos existentes sin outbox ni declaración en AsyncAPI; se corrige el "dispara y olvida" (R12) | ⚠️ |
| VI. Observabilidad | Logs con `eventId`/`linkId`, nunca token ni datos personales; métricas Micrometer existentes; contador de `429` por ruta en el tablero del gateway (T-I13) | ✅ |
| VII. Rendimiento | Cupo con `$inc` condicional (R1); índices declarados; listas paginadas (máx. 100); escenarios k6 (quickstart §7) | ✅ |
| VIII. Entrega inmutable | Sin servicios nuevos; `INVITE_LINK_BASE_URL` por entorno (`.env.example`, `DOTENV_PRODUCTION`, `docker-compose.prod.yml`) | ✅ |
| IX. Privacidad | Asistentes solo para el organizador; contador sin identidades; `stats` reducido para no organizadores (R7); vista previa sin dirección; `hideAddressUntilAccepted` respetado en `scope=invited` | ✅ |
| X. Simplicidad | Sin tecnologías nuevas; una colección más en un servicio existente | ✅ |

**Resultado del gate:** pasa con dos desviaciones justificadas en *Complexity Tracking*.
**Re-check tras el diseño (Fase 1):** sin cambios; el diseño no agrega desviaciones.

## Project Structure

### Documentation (this feature)

```text
specs/003-ola-1-eventos-invitaciones/
├── plan.md                        # Este archivo
├── research.md                    # Fase 0: R1–R14
├── data-model.md                  # Fase 1: entidades, índices y transiciones
├── quickstart.md                  # Fase 1: validación de extremo a extremo
├── contracts/ola-1-operations.md  # Fase 1: insumo de openapi.yaml (no es el contrato)
├── checklists/requirements.md
└── tasks.md                       # Fase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
docs/api/
├── openapi.yaml                   # 13 operaciones y schemas (tarea de contrato)
├── CONVENTIONS.md                 # X-User-Name
└── fixtures/events/, fixtures/auth/
docs/decisions/adr-014-*.md, adr-016-*.md   # enmiendas: scope=invited; fila invitación vs RSVP cerrada

backend/event-service/src/main/java/com/vito/event/
├── domain/model/                  # Event (organizerDisplayName), Invitation (inviteLinkId),
│                                  #   RSVP (displayName, waitlistedAt), InviteLink (nuevo)
├── domain/port/inbound/           # EventService: updateEvent, cancelEvent, respondRsvp, enlaces
├── domain/port/outbound/          # InviteLinkRepository (nuevo), contadores atómicos
├── application/service/           # EventServiceImpl; InviteLinkService (nuevo)
└── infrastructure/
    ├── adapter/inbound/rest/      # EventController (PATCH, sin invitations/respond),
    │                              #   InviteLinkController (nuevo)
    ├── adapter/outbound/persistence/  # $inc condicional, índices, InviteLinkMongoAdapter
    ├── adapter/outbound/messaging/    # whenComplete en el send
    └── config/SecurityConfig.java     # GET /api/v1/invite-links/* sin autenticación
backend/event-service/src/test/    # Testcontainers MongoDB, concurrencia, contrato

backend/auth-service/              # claim `name` en el access token; /me alineado a AuthUser
backend/api-gateway/               # rutas públicas por método+patrón; ruta /api/v1/invite-links/**;
                                   #   rate limit por IP (preview); X-User-Name desde el claim
backend/load-tests/ola-1-rsvp.js, ola-1-preview.js

frontend/vito_app/lib/
├── config/routes/app_router.dart  # ruta /i/<token>
└── features/events/
    ├── data/                      # datasource y modelos: Rsvp, InviteLink, preview, PATCH
    ├── domain/usecases/           # update_event, cancel_event, respond_rsvp (ajuste),
    │                              #   create_invite_link, accept_invite_link, …
    └── presentation/              # editar evento, enlace (copiar/WhatsApp), vista previa,
                                   #   RSVP con lista de espera, contador de confirmados

infrastructure/ y .env.example     # INVITE_LINK_BASE_URL
```

**Structure Decision**: sin servicios nuevos (Principio X). `event-service` es dueño de
invitaciones, enlaces y RSVP (ADR-016 §6). El reparto de tareas por dueño sigue los
CODEOWNERS:

| Bloque | Dueño |
|--------|-------|
| Contrato (`docs/api/**`), fixtures y enmiendas a ADR-014/016 | Agente de Alex (pista C) |
| App Flutter (`frontend/**`) | Agente de Alex (pista F) |
| `event-service`, `auth-service`, gateway, k6 | Sesión de Bernardo (pista B) |
| `INVITE_LINK_BASE_URL` en `.env.example`, Compose y secretos de producción | Sesión de Bernardo (pista I) |

Orden: contrato → (backend ∥ app contra el mock de Prism) → integración y E2E.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| V: eventos Kafka sin sobre versionado, sin outbox y sin declarar en `asyncapi.yaml` | No hay ningún consumidor todavía; el outbox y la declaración son transversales a los 9 productores | Hacerlo aquí duplica la ola y bloquea Bug-F07/F09. Se resuelve con T-C05 y antes del primer consumidor (ticket QR, Ola 2); se registra como tarea en `/speckit-tasks` |
| II: sin ArchUnit en `event-service` | La verificación automática de capas es transversal a los 10 servicios | Agregarlo solo aquí deja la regla a medias; va con T-B18 (`backend/shared-web`) |
