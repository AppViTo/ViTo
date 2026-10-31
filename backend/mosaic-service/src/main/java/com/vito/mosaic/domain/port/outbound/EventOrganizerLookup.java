package com.vito.mosaic.domain.port.outbound;

import java.util.Optional;

/**
 * Organizador de un evento. El dueño del dato es event-service (ADR-016, regla 7): mosaic-service
 * lo consulta, no lo replica.
 */
public interface EventOrganizerLookup {

    /**
     * @return el {@code organizerId} del evento, o vacío si el evento no existe o el usuario actual
     *         no puede verlo
     */
    Optional<String> findOrganizerId(String eventId);
}
