package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.RSVP;

/**
 * Cuerpo de respondRsvp ({@code RespondRsvpRequest} del contrato). {@code status} llega como
 * texto para que un valor desconocido dé el 400 del fixture y no un error de lectura del JSON.
 */
public record RespondRsvpRequest(String status, String message) {

    /** {@code PENDING} y {@code WAITLISTED} se convierten aquí y el servicio los rechaza con el mismo 400. */
    RSVP.RSVPStatus parsedStatus() {
        if (status == null) {
            throw EventException.invalidRsvpStatus();
        }
        try {
            return RSVP.RSVPStatus.valueOf(status);
        } catch (IllegalArgumentException unknown) {
            throw EventException.invalidRsvpStatus();
        }
    }
}
