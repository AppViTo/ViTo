package com.vito.auth.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vito.auth.domain.exception.AuthenticationException;
import com.vito.auth.domain.exception.AuthorizationException;
import com.vito.auth.domain.exception.UserAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    static final String UNAUTHORIZED = "UNAUTHORIZED";
    static final String FORBIDDEN = "FORBIDDEN";
    static final String CONFLICT = "CONFLICT";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationException(AuthorizationException ex) {
        log.warn("Authorization error: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.info("Registration conflict: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, CONFLICT, ex.getMessage());
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

    /** Sin header Authorization en logout: mismo 401 que un token inválido (logoutUser 401). */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(ex.getHeaderName())) {
            return handleAuthenticationException(AuthenticationException.invalidAccessToken());
        }
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Falta el header " + ex.getHeaderName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "La petición contiene datos no válidos");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR,
                "Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos");
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
