# Data Model: Ola 1 del contrato — eventos, invitaciones y RSVP

**Feature**: [spec.md](./spec.md) · **Research**: [research.md](./research.md) · **Fecha**: 2026-11-01

Todo vive en la base de `event-service` (Principio II). Las fechas se guardan como instante
UTC (`Instant`) y viajan como ISO-8601 con `Z` (Bug-B01). "Nuevo" marca lo que agrega esta ola.

## Event (colección `events`, existente)

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | string | |
| `organizerId` | string | No editable |
| `organizerDisplayName` | string, nullable | **Nuevo.** Desde `X-User-Name` al crear o editar (R6) |
| `title`, `description`, `details.*`, `coverImage`, `category` | — | Editables por `PATCH` (R8) |
| `settings.isPublic`, `settings.maxAttendees`, `settings.hideAddressUntilAccepted`, … | — | Editables; `maxAttendees` 0 = sin límite |
| `status` | `DRAFT \| PUBLISHED \| CANCELLED \| COMPLETED` | No editable por `PATCH`; `DELETE` → `CANCELLED` |
| `stats.totalAccepted` | int | Fuente del cupo; solo cambia con `$inc` condicional (R1) |
| `stats.totalInvited`, `totalDeclined`, `totalMaybe`, `totalWaitlisted` | int | Solo `$inc`. Visibles solo para el organizador (R7) |

**Validaciones de edición:** iguales a `createEvent`; `endDate > startDate`;
`maxAttendees == 0 || maxAttendees >= stats.totalAccepted` (si no, 409).

**Transiciones de estado:**

```text
PUBLISHED ──DELETE──▶ CANCELLED   (idempotente; COMPLETED ──DELETE──▶ 409)
PUBLISHED ──endDate pasada──▶ COMPLETED   (fuera de esta ola; se trata como vencido por fecha)
```

## Invitation (colección `invitations`, existente)

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | string | |
| `eventId`, `inviterId`, `inviteeId` | string | Único `{eventId, inviteeId}` |
| `status` | `PENDING \| ACCEPTED \| DECLINED \| EXPIRED` | `EXPIRED` se calcula al leer si `PENDING` y `expiresAt` pasó |
| `message` | string ≤ 280, nullable | |
| `inviteLinkId` | string, nullable | **Nuevo.** Enlace de origen; null si fue por la app |
| `sentAt`, `expiresAt`, `respondedAt` | instant | `expiresAt` = `sentAt` + 7 días por la app; null por enlace (ya aceptada) |

Índices: único `{eventId, inviteeId}`; `{inviteeId, status}` para `scope=invited` (R10).

**Transiciones** (las dispara el RSVP, FR-017):

```text
PENDING ──RSVP ACCEPTED/WAITLISTED──▶ ACCEPTED
PENDING ──RSVP DECLINED──▶ DECLINED
PENDING ──RSVP MAYBE──▶ PENDING
ACCEPTED ⇄ DECLINED  (si la persona cambia su RSVP)
PENDING ──expiresAt──▶ EXPIRED  (no puede responder por esta invitación)
```

## InviteLink (colección `invite_links`, nueva)

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | string | |
| `eventId` | string | Índice `{eventId, revoked, expiresAt}` |
| `token` | string (22, base64url) | Único. Nunca en logs (R4) |
| `createdBy` | string | Organizador |
| `expiresAt` | instant | `createdAt` + `expiresInDays` (1–30, por defecto 7) |
| `maxUses` | int 1–1000, nullable | null = sin límite |
| `uses` | int ≥ 0 | `$inc` condicional al unirse una persona nueva |
| `revoked` | boolean | Revocar es irreversible |
| `createdAt` | instant | |

`url` no se guarda: se arma al responder con `INVITE_LINK_BASE_URL` (R4).

**Vigente** = `!revoked && expiresAt > now && (maxUses == null || uses < maxUses)` y evento
`PUBLISHED` con `endDate` futura. Vencido, revocado o inexistente → el mismo 404 (FR-006);
límite de usos alcanzado o evento no vigente → 409 (FR-007).

## RSVP (colección `rsvps`, existente)

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | string | |
| `eventId`, `userId` | string | Único `{eventId, userId}` |
| `displayName` | string, nullable | **Nuevo.** Desde `X-User-Name` al responder (R6) |
| `status` | `PENDING \| ACCEPTED \| DECLINED \| MAYBE \| WAITLISTED` | Entrada solo `ACCEPTED/DECLINED/MAYBE` |
| `message` | string ≤ 280, nullable | |
| `respondedAt` | instant | Última respuesta |
| `waitlistedAt` | instant, nullable | **Nuevo.** Orden de la lista de espera |
| `guestsCount` | — | **Se retira** del contrato (R2) |
| `checkInQrCode`, `checkedInAt` | — | Fuera del contrato; pasan a `checkin-service` en la Ola 2 |

Índice `{eventId, status, waitlistedAt}` para la promoción.

**Transiciones** (R1):

```text
(ninguno) ──ACCEPTED, hay cupo──▶ ACCEPTED          stats.totalAccepted +1
(ninguno) ──ACCEPTED, sin cupo──▶ WAITLISTED        waitlistedAt = now
ACCEPTED ──DECLINED/MAYBE──▶ DECLINED/MAYBE         totalAccepted −1 → promover al WAITLISTED más antiguo
WAITLISTED ──lugar libre──▶ ACCEPTED                promoción automática
WAITLISTED ──DECLINED/MAYBE──▶ DECLINED/MAYBE       sale de la lista
X ──misma respuesta──▶ X                            sin cambios (FR-019)
```

## Vistas de lectura (no se guardan)

- **InviteLinkPreview:** `eventId`, `title`, `startDate`, `endDate`, `location` (lugar
  general, **nunca** dirección exacta ni coordenadas), `coverImage`, `category`,
  `organizerDisplayName` (o "El organizador"), `expiresAt` del enlace.
- **Attendee** (solo organizador): `userId`, `displayName`, `status`, `respondedAt`. Sin
  correo ni teléfono.
- **Event para no organizadores:** `stats` con solo `totalAccepted` (R7) y dirección según
  `hideAddressUntilAccepted`.
