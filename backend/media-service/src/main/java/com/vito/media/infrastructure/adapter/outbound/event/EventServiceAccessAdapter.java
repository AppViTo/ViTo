package com.vito.media.infrastructure.adapter.outbound.event;

import com.vito.media.domain.exception.MediaException;
import com.vito.media.domain.model.EventAccess;
import com.vito.media.domain.port.outbound.EventAccessLookup;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

/**
 * T-B33: pregunta a event-service, dueño del evento y de los RSVP (ADR-016), por la ruta
 * {@code INTERNO} {@code GET /internal/v1/events/{eventId}/my-access}, con el mismo Bearer de la
 * petición en curso (como {@code EventServiceOrganizerAdapter} de mosaic-service). La ruta solo
 * informa del usuario del token, así que no expone el RSVP de nadie más.
 */
@Slf4j
@Component
public class EventServiceAccessAdapter implements EventAccessLookup {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;

    @Autowired
    public EventServiceAccessAdapter(RestClient.Builder builder,
                                     @Value("${event-service.uri}") String eventServiceUri) {
        this(builder.clone().requestFactory(timeouts()).baseUrl(eventServiceUri).build());
    }

    /** Para pruebas: un cliente enlazado a {@code MockRestServiceServer}. */
    EventServiceAccessAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public Optional<EventAccess> findAccess(String eventId) {
        try {
            AccessResponse response = restClient.get()
                    .uri("/internal/v1/events/{eventId}/my-access", eventId)
                    .headers(headers -> currentAuthorization().ifPresent(
                            value -> headers.set(HttpHeaders.AUTHORIZATION, value)))
                    .retrieve()
                    .body(AccessResponse.class);
            if (response == null) {
                throw new MediaException.DependencyUnavailableException(null);
            }
            return Optional.of(new EventAccess(response.organizerId(), response.rsvpStatus()));
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            if (status == HttpStatus.UNAUTHORIZED) {
                throw new MediaException.NotAuthenticatedException();
            }
            throw new MediaException.DependencyUnavailableException(e);
        } catch (RestClientException e) {
            // 5xx, timeout o conexión rechazada
            throw new MediaException.DependencyUnavailableException(e);
        }
    }

    private static Optional<String> currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION));
        }
        return Optional.empty();
    }

    record AccessResponse(String eventId, String organizerId, String rsvpStatus) {
    }
}
