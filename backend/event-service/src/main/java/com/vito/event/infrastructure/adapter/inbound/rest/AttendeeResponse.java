package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.domain.model.RSVP;

import java.time.Instant;

/**
 * {@code Attendee} del contrato (FR-024): sin correo, teléfono, QR ni check-in. {@code displayName}
 * es el que llegó en {@code X-User-Name} al responder (R6); null en respuestas anteriores.
 */
public record AttendeeResponse(String userId, String displayName, String status, Instant respondedAt) {

    static AttendeeResponse from(RSVP rsvp) {
        return new AttendeeResponse(rsvp.getUserId(), rsvp.getDisplayName(), rsvp.getStatus().name(),
                rsvp.getRespondedAt());
    }
}
