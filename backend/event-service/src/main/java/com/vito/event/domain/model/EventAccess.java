package com.vito.event.domain.model;

/**
 * T-B33: qué relación tiene una persona con un evento, para los servicios que deciden acceso con
 * esa regla (media-service: organizador o RSVP {@code ACCEPTED}, decisión 3 de T-C09).
 *
 * @param rsvpStatus {@code null} si no ha respondido
 */
public record EventAccess(String eventId, String organizerId, RSVP.RSVPStatus rsvpStatus) {
}
