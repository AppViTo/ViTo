package com.vito.mosaic.infrastructure.adapter.outbound.event;

import com.vito.mosaic.domain.port.outbound.EventOrganizerLookup;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;

/**
 * Pregunta el organizador a event-service, dueño del evento (ADR-016, regla 7), con
 * {@code GET /api/v1/events/{eventId}} y el mismo Bearer de la petición en curso: event-service
 * aplica sus reglas de visibilidad, y si el usuario no puede ver el evento tampoco lo organiza.
 *
 * <p>Llamada síncrona y no réplica por Kafka: retirar una foto es una acción rara del
 * organizador, sin meta de latencia como la validación del QR.
 */
@Slf4j
@Component
public class EventServiceOrganizerAdapter implements EventOrganizerLookup {

    private final RestClient restClient;

    public EventServiceOrganizerAdapter(RestClient.Builder builder,
                                        @Value("${event-service.uri}") String eventServiceUri) {
        this.restClient = builder.baseUrl(eventServiceUri).build();
    }

    @Override
    public Optional<String> findOrganizerId(String eventId) {
        try {
            Map<?, ?> event = restClient.get()
                    .uri("/api/v1/events/{eventId}", eventId)
                    .headers(headers -> currentAuthorization().ifPresent(
                            value -> headers.set(HttpHeaders.AUTHORIZATION, value)))
                    .retrieve()
                    .body(Map.class);
            Object organizerId = event == null ? null : event.get("organizerId");
            return organizerId instanceof String id && !id.isBlank() ? Optional.of(id) : Optional.empty();
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND || status == HttpStatus.FORBIDDEN) {
                log.info("event-service answered {} for event {}", e.getStatusCode().value(), eventId);
                return Optional.empty();
            }
            throw e;
        }
    }

    private static Optional<String> currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION));
        }
        return Optional.empty();
    }
}
