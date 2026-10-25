package com.vito.event.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vito.event.domain.exception.EventException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envelope único de error de ADR-003 / CONVENTIONS §3.3: {@code error} en SCREAMING_SNAKE de la
 * lista canónica, {@code message} en es-ES, {@code status} y {@code timestamp} ISO-8601 UTC.
 * Copia del handler de auth-service adaptada a {@link EventException} (ADR-017: copia temporal hasta T-B18).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    static final String UNAUTHORIZED = "UNAUTHORIZED";
    static final String FORBIDDEN = "FORBIDDEN";
    static final String NOT_FOUND = "NOT_FOUND";
    static final String CONFLICT = "CONFLICT";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    @ExceptionHandler(EventException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(EventException ex) {
        log.info("Domain error ({}): {}", ex.getReason(), ex.getMessage());
        return switch (ex.getReason()) {
            case INVALID -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                    HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage(), ex.getDetails()));
            case UNAUTHORIZED -> error(HttpStatus.UNAUTHORIZED, UNAUTHORIZED, ex.getMessage());
            case FORBIDDEN -> error(HttpStatus.FORBIDDEN, FORBIDDEN, ex.getMessage());
            case NOT_FOUND -> error(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage());
            case CONFLICT -> error(HttpStatus.CONFLICT, CONFLICT, ex.getMessage());
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.info("Validation errors: {}", details);

        String message = details.size() == 1
                ? "El campo " + details.keySet().iterator().next() + " no es válido"
                : "Hay " + details.size() + " campos con errores";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, message, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.info("Unreadable request body: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "El cuerpo de la petición no es un JSON válido");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Falta el header " + ex.getHeaderName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "La petición contiene datos no válidos");
    }

    /**
     * Excepciones propias de Spring MVC (ruta inexistente, método no soportado, parámetro que
     * falta o con tipo erróneo...): conservan su status en vez de convertirse en 500. Diferencia
     * con el handler de auth-service, que las responde con 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        if (ex instanceof TypeMismatchException) {
            return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "La petición contiene datos no válidos");
        }
        if (ex instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            return springClientError(springError.getStatusCode());
        }
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR,
                "Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos");
    }

    private static ResponseEntity<ErrorResponse> springClientError(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        return switch (status) {
            case NOT_FOUND -> error(status, NOT_FOUND, "El recurso solicitado no existe");
            case UNAUTHORIZED -> error(status, UNAUTHORIZED, "Debes iniciar sesión");
            case FORBIDDEN -> error(status, FORBIDDEN, "No tienes permiso para realizar esta acción");
            case CONFLICT -> error(status, CONFLICT, "La petición entra en conflicto con el estado actual");
            default -> error(status, VALIDATION_ERROR, "La petición no es válida");
        };
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, error, message, null));
    }

    /** Schema ErrorResponse de openapi.yaml; {@code details} solo en validación. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String error, String message, int status, String timestamp,
                                Map<String, String> details) {

        static ErrorResponse of(HttpStatus status, String error, String message, Map<String, String> details) {
            return new ErrorResponse(error, message, status.value(),
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), details);
        }
    }
}
