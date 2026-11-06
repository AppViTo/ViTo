package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.domain.model.Invitation;

import java.time.Instant;

/**
 * {@code Invitation} del contrato. El modelo de dominio tiene {@code isExpired()}, que Jackson
 * publicaría como un campo {@code expired} que el contrato no declara.
 */
public record InvitationResponse(String id, String eventId, String inviterId, String inviteeId, String status,
                                 String message, String inviteLinkId, Instant sentAt, Instant expiresAt,
                                 Instant respondedAt) {

    static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getEventId(), invitation.getInviterId(),
                invitation.getInviteeId(), invitation.getStatus() == null ? null : invitation.getStatus().name(),
                invitation.getMessage(), invitation.getInviteLinkId(), invitation.getSentAt(),
                invitation.getExpiresAt(), invitation.getRespondedAt());
    }
}
