package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.domain.exception.EventException;
import com.vito.event.domain.model.EventAccess;
import com.vito.event.domain.port.inbound.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas {@code INTERNO} (endpoint-inventory): las usan otros servicios por el DNS de Compose; el
 * gateway no las enruta y no entran a {@code openapi.yaml}.
 */
@RestController
@RequiredArgsConstructor
public class InternalEventController {

    private final EventService eventService;

    /**
     * T-B33: organizador del evento y RSVP de quien llama, para el acceso de media-service
     * (organizador o RSVP {@code ACCEPTED}). Con el Bearer reenviado, solo informa del usuario del
     * token: nadie puede preguntar por el RSVP de otra persona.
     */
    @GetMapping("/internal/v1/events/{eventId}/my-access")
    public EventAccess myAccess(@PathVariable String eventId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw EventException.unauthenticated();
        }
        return eventService.getAccess(eventId, authentication.getName());
    }
}
