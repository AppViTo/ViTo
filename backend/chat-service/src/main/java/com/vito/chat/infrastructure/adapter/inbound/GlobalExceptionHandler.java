package com.vito.chat.infrastructure.adapter.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vito.chat.domain.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Envelope único de error de ADR-003 / CONVENTIONS §3.3: {@code error} en SCREAMING_SNAKE de la
 * lista canónica, {@code message}, {@code status} y {@code timestamp} ISO-8601 UTC.
 * Copia del handler de auth-service (ADR-017).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    static final String UNAUTHORIZED = "UNAUTHORIZED";
    static final String FORBIDDEN = "FORBIDDEN";
    static final String NOT_FOUND = "NOT_FOUND";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    @ExceptionHandler(ChatException.RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(ChatException.RoomNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ChatException.MessageNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotFound(ChatException.MessageNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ChatException.UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(ChatException.UnauthorizedException ex) {
        return error(HttpStatus.FORBIDDEN, FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ChatException.RoomFullException.class)
    public ResponseEntity<ErrorResponse> handleRoomFull(ChatException.RoomFullException ex) {
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage());
    }

    /**
     * Excepciones propias de Spring MVC (ruta inexistente, método no soportado...) conservan su
     * status en vez de convertirse en 500 (T-B28).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
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
