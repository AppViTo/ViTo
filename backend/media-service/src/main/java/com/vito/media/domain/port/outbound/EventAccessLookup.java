package com.vito.media.domain.port.outbound;

import com.vito.media.domain.model.EventAccess;

import java.util.Optional;

/** T-B33: organizador del evento y RSVP de quien hace la petición en curso. */
public interface EventAccessLookup {

    /**
     * @return vacío si el evento no existe
     * @throws com.vito.media.domain.exception.MediaException.NotAuthenticatedException si event-service rechaza el token
     * @throws com.vito.media.domain.exception.MediaException.DependencyUnavailableException si event-service no responde
     */
    Optional<EventAccess> findAccess(String eventId);
}
