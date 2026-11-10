package com.vito.shared.web.error;

import static com.vito.shared.web.error.ErrorCodes.CONFLICT;
import static com.vito.shared.web.error.ErrorCodes.FORBIDDEN;
import static com.vito.shared.web.error.ErrorCodes.INTERNAL_ERROR;
import static com.vito.shared.web.error.ErrorCodes.NOT_FOUND;
import static com.vito.shared.web.error.ErrorCodes.UNAUTHORIZED;
import static com.vito.shared.web.error.ErrorCodes.VALIDATION_ERROR;

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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handler base del envelope de error de ADR-003 (T-B18, ADR-017). Cada servicio lo extiende con
 * su {@code @RestControllerAdvice}, agrega el handler de sus excepciones de dominio y puede
 * redefinir cualquiera de estos métodos (sin repetir {@code @ExceptionHandler}).
 *
 * <p>Las excepciones propias de Spring MVC (ruta inexistente, método no soportado, parámetro que
 * falta o con tipo erróneo...) conservan su status 4xx en vez de convertirse en 500 (T-B28).
 */
@Slf4j
public abstract class BaseExceptionHandler {

    static final String UNEXPECTED_MESSAGE = "Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos";
    static final String INVALID_DATA_MESSAGE = "La petición contiene datos no válidos";

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
        return validationError(message, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.info("Unreadable request body: {}", redact(ex.getMessage()));
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "El cuerpo de la petición no es un JSON válido");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Falta el header " + ex.getHeaderName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", redact(ex.getMessage()));
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, INVALID_DATA_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        if (ex instanceof TypeMismatchException) {
            return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, INVALID_DATA_MESSAGE);
        }
        if (ex instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            return springClientError(springError.getStatusCode());
        }
        String message = redact(ex.getMessage());
        if (message != null && !message.equals(ex.getMessage())) {
            // T067: la traza de la pila repetiría el dato redactado; solo el tipo
            log.error("Unexpected error: {} ({})", message, ex.getClass().getName());
        } else {
            log.error("Unexpected error: {}", message, ex);
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, UNEXPECTED_MESSAGE);
    }

    /**
     * Traduce el {@code Reason} de una excepción de dominio (INVALID, UNAUTHORIZED, FORBIDDEN,
     * NOT_FOUND, CONFLICT: el mismo enum en todos los servicios) a su status y código.
     *
     * @param details solo para INVALID; null si no hay
     */
    protected ResponseEntity<ErrorResponse> domainError(Enum<?> reason, String message, Map<String, String> details) {
        log.info("Domain error ({}): {}", reason, redact(message));
        return switch (reason.name()) {
            case "INVALID" -> validationError(message, details);
            case "UNAUTHORIZED" -> error(HttpStatus.UNAUTHORIZED, UNAUTHORIZED, message);
            case "FORBIDDEN" -> error(HttpStatus.FORBIDDEN, FORBIDDEN, message);
            case "NOT_FOUND" -> error(HttpStatus.NOT_FOUND, NOT_FOUND, message);
            case "CONFLICT" -> error(HttpStatus.CONFLICT, CONFLICT, message);
            default -> throw new IllegalArgumentException("Reason sin traducción: " + reason);
        };
    }

    /**
     * Texto apto para el log. Por defecto lo devuelve igual; un servicio lo redefine para ocultar
     * datos sensibles que pueden aparecer en mensajes de Spring (T067: token del enlace).
     */
    protected String redact(String message) {
        return message;
    }

    protected static ResponseEntity<ErrorResponse> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, error, message, null));
    }

    protected static ResponseEntity<ErrorResponse> validationError(String message, Map<String, String> details) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, message, details));
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
}
