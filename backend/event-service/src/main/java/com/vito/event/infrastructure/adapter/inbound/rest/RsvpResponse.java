package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.domain.model.RSVP;

import java.time.Instant;

/**
 * {@code Rsvp} del contrato: sin id interno, QR, nombre ni check-in. {@code message} y
 * {@code waitlistedAt} salen como {@code null} cuando no tienen valor, igual que los fixtures.
 */
public record RsvpResponse(String eventId, String userId, String status, String message,
                           Instant respondedAt, Instant waitlistedAt) {

    static RsvpResponse from(RSVP rsvp) {
        return new RsvpResponse(rsvp.getEventId(), rsvp.getUserId(), rsvp.getStatus().name(),
                rsvp.getMessage(), rsvp.getRespondedAt(), rsvp.getWaitlistedAt());
    }
}
