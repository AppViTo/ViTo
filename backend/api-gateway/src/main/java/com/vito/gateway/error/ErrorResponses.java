package com.vito.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envelope único de error de ADR-003 / CONVENTIONS §3.3 para las respuestas que genera el
 * propio gateway: {@code {error, message, status, timestamp}}, {@code error} en SCREAMING_SNAKE,
 * {@code message} en es-ES y {@code timestamp} ISO-8601 UTC con {@code Z}.
 */
public final class ErrorResponses {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorResponses() {
    }

    public static Map<String, Object> body(HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("status", status.value());
        body.put("timestamp", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        return body;
    }

    /** Escribe el envelope como cuerpo de la respuesta con el status indicado. */
    public static Mono<Void> write(ServerHttpResponse response, HttpStatus status, String error, String message) {
        byte[] json;
        try {
            json = MAPPER.writeValueAsBytes(body(status, error, message));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(json.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
    }
}
