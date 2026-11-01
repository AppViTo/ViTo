# Contrato propuesto: Ola 1 (insumo de `docs/api/openapi.yaml`)

**Feature**: [../spec.md](../spec.md) · **Research**: [../research.md](../research.md)

Este archivo **no es el contrato**. Es el diseño que la tarea de contrato de la Ola 1 pasa a
`docs/api/openapi.yaml` y `docs/api/fixtures/`, por PR en `contract/ola-1` con issue
`contract-change`, D-08 de la Ola 1 aprobada y 1 CODEOWNER (Principio I). Los schemas
detallados del RFC #204 (`Invitation`, `InviteLink`, `CreateInviteLinkRequest`,
`InviteLinkPreview`) se reutilizan con los ajustes marcados aquí.

Reglas comunes (CONVENTIONS): `Authorization: Bearer` salvo donde se indica; errores con el
envelope de ADR-003 y mensaje en español; listas planas con `page`/`size` (máx. 100); fechas
ISO-8601 UTC con `Z`; `429` con `Retry-After`.

## Operaciones

| operationId | Método y ruta | Auth | Éxito | Errores |
|-------------|---------------|------|-------|---------|
| `updateEvent` | `PATCH /api/v1/events/{eventId}` (`application/merge-patch+json`) | Organizador | `200 Event` | 400 validación o campo no editable · 401 · 403 no es el organizador · 404 · 409 cupo < confirmados, evento cancelado o terminado |
| `cancelEvent` | `DELETE /api/v1/events/{eventId}` | Organizador | `204` (idempotente) | 401 · 403 · 404 · 409 evento terminado |
| `respondRsvp` | `POST /api/v1/events/{eventId}/rsvp` | Usuario | `200 Rsvp` (`status` puede ser `WAITLISTED`) | 400 `status` inválido · 401 · 404 evento inexistente **o privado sin acceso** · 409 organizador, evento cancelado, terminado o pasado · 429 |
| `listEventAttendees` | `GET /api/v1/events/{eventId}/attendees?status&page&size` | Organizador | `200 Attendee[]` | 400 · 401 · 403 cualquier otro, aunque esté confirmado · 404 |
| `sendInvitation` | `POST /api/v1/events/{eventId}/invitations` | Organizador | `201 Invitation` (`PENDING`, vence en 7 días) | 400 · 401 · 403 · 404 · 409 ya invitado (ADR-009) o evento no vigente |
| `listEventInvitations` | `GET /api/v1/events/{eventId}/invitations?page&size` | Organizador | `200 Invitation[]` | 400 · 401 · 403 · 404 |
| `createInviteLink` | `POST /api/v1/events/{eventId}/invite-links` | Organizador | `201 InviteLink` | 400 `expiresInDays` fuera de 1–30, `maxUses` fuera de 1–1000 · 401 · 403 · 404 · 409 evento no vigente |
| `listInviteLinks` | `GET /api/v1/events/{eventId}/invite-links?page&size` | Organizador | `200 InviteLink[]` (vigentes primero) | 401 · 403 · 404 |
| `revokeInviteLink` | `DELETE /api/v1/events/{eventId}/invite-links/{linkId}` | Organizador | `204` (idempotente) | 401 · 403 · 404 |
| `getInviteLinkPreview` | `GET /api/v1/invite-links/{token}` | **Pública** (`security: []`) | `200 InviteLinkPreview` | 404 único "El enlace no existe o ya no está disponible" · 429 por IP |
| `acceptInviteLink` | `POST /api/v1/invite-links/{token}/accept` | Usuario | `200 Invitation` (idempotente; organizador → 200 sin crear) | 401 · 404 único · 409 límite de usos o evento no vigente · 429 |
| `listEvents` (cambio) | `GET /api/v1/events?scope=mine\|upcoming\|invited` | Usuario | `200 Event[]` | 400 `scope` inválido (fixture existente) · 401 |
| `getCurrentUser` | `GET /api/v1/auth/me` | Usuario | `200 AuthUser` (ADR-004) | 401 |

**Retirada:** `POST /api/v1/events/{eventId}/invitations/respond` (D-01; no estaba en el
contrato, se borra del código).

## Schemas nuevos o modificados

- **`Rsvp`** (nuevo en el contrato): `eventId`, `userId`, `status` (enum de CONVENTIONS §6),
  `message` (nullable), `respondedAt`, `waitlistedAt` (nullable). Sin `guestsCount` ni QR.
- **`RespondRsvpRequest`:** `status` ∈ `ACCEPTED | DECLINED | MAYBE` (requerido), `message`
  ≤ 280 (opcional).
- **`Attendee`:** `userId`, `displayName` (nullable), `status`, `respondedAt`. Sin correo ni teléfono.
- **`UpdateEventRequest`:** subconjunto editable de `details` y `settings` (todos opcionales,
  `minProperties: 1`); `additionalProperties: false` para que `stats`, `status`, `id` y
  `organizerId` den 400.
- **`Invitation`, `InviteLink`, `CreateInviteLinkRequest`, `InviteLinkPreview`,
  `SendInvitationRequest`:** como en el RFC #204. `InviteLinkPreview.organizerDisplayName`
  puede ser "El organizador" (R6).
- **`EventStats`:** la descripción documenta que quien no es organizador recibe solo
  `totalAccepted` (R7). Fixture de cada vista (`getEvent.200.organizer.json`,
  `getEvent.200.guest.json`).
- **`listEvents.scope`:** enum `mine | upcoming | invited` (enmienda a ADR-014).

## Cambios en CONVENTIONS

- Tabla de cabeceras: `X-User-Name` (claim `name`), con la misma higiene que los demás
  `X-User-*` (el gateway descarta la del cliente, T-B01) (R6).

## Fixtures mínimos (Principio I, SC-004)

Por operación, un `*.200.json`/`*.201.json`/`*.204` y al menos un error. Obligatorios además:
`respondRsvp.200.waitlisted.json`, `respondRsvp.404.private.json`,
`getInviteLinkPreview.404.json` (único para vencido, revocado o inexistente),
`acceptInviteLink.409.max-uses.json`, `listEventAttendees.403.json`,
`updateEvent.409.capacity.json`, `listEvents.200.invited.json`, `getCurrentUser.200.json`.
