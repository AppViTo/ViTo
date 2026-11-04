package com.vito.event.domain.model;

import java.time.Instant;

/**
 * Vista previa pública de un enlace (FR-003): lo mínimo para mostrar la invitación sin sesión.
 * Nunca lleva dirección exacta ni coordenadas, aunque el evento no oculte la dirección.
 */
public record InviteLinkPreview(String eventId, String title, Instant startDate, Instant endDate,
                                String location, String coverImage, String category,
                                String organizerDisplayName, Instant expiresAt) {

    /** Cuando el evento no guardó el nombre del organizador (eventos anteriores a R6). */
    public static final String DEFAULT_ORGANIZER_NAME = "El organizador";

    public static InviteLinkPreview of(Event event, InviteLink link) {
        Event.EventDetails details = event.getDetails();
        String cover = details == null || details.getCoverImages() == null || details.getCoverImages().isEmpty()
                ? null : details.getCoverImages().get(0);
        String organizer = event.getOrganizerDisplayName() == null || event.getOrganizerDisplayName().isBlank()
                ? DEFAULT_ORGANIZER_NAME : event.getOrganizerDisplayName();
        return new InviteLinkPreview(event.getId(), event.getTitle(),
                details == null ? null : details.getStartDate(),
                details == null ? null : details.getEndDate(),
                details == null ? null : details.getLocation(),
                cover,
                details == null || details.getCategory() == null ? null : String.valueOf(details.getCategory()),
                organizer, link.getExpiresAt());
    }
}
