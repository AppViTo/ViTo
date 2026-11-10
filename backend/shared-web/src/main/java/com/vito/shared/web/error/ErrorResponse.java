package com.vito.shared.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Schema {@code ErrorResponse} de openapi.yaml (ADR-003, CONVENTIONS §3.3): {@code error} en
 * SCREAMING_SNAKE de la lista canónica, {@code message} en es-ES, {@code status} y
 * {@code timestamp} ISO-8601 UTC. {@code details} solo en validación.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String message, int status, String timestamp,
                            Map<String, String> details) {

    public static ErrorResponse of(HttpStatus status, String error, String message, Map<String, String> details) {
        return new ErrorResponse(error, message, status.value(),
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), details);
    }
}
