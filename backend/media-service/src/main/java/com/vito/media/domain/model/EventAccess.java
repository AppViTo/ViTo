package com.vito.media.domain.model;

/**
 * T-B33: relación de quien pide con el evento, según event-service (dueño del evento, ADR-016).
 * Decisión 3 de T-C09: listan y piden URL el organizador y quien tiene RSVP {@code ACCEPTED}.
 *
 * @param rsvpStatus {@code null} si no ha respondido
 */
public record EventAccess(String organizerId, String rsvpStatus) {

    public boolean isOrganizer(String userId) {
        return userId != null && userId.equals(organizerId);
    }

    public boolean canView(String userId) {
        return isOrganizer(userId) || "ACCEPTED".equals(rsvpStatus);
    }
}
