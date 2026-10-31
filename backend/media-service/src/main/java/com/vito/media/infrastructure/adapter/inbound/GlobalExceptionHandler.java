package com.vito.media.infrastructure.adapter.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vito.media.domain.exception.MediaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Envelope único de error de ADR-003 / CONVENTIONS §3.3 en todas las respuestas 4xx/5xx.
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

    @Value("${media.max-file-size:10485760}")
    private long maxFileSize;

    @ExceptionHandler(MediaException.MediaNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMediaNotFound(MediaException.MediaNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MediaException.UploadFailedException.class)
    public ResponseEntity<ErrorResponse> handleUploadFailed(MediaException.UploadFailedException ex) {
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(MediaException.ProcessingFailedException.class)
    public ResponseEntity<ErrorResponse> handleProcessingFailed(MediaException.ProcessingFailedException ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, ex.getMessage());
    }

    @ExceptionHandler(MediaException.UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(MediaException.UnauthorizedException ex) {
        return error(HttpStatus.FORBIDDEN, FORBIDDEN, ex.getMessage());
    }

    /** Archivo vacío, de un tipo no permitido, que no coincide con su contenido o > 10 MB (T-B19, ADR-003). */
    @ExceptionHandler(MediaException.InvalidFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFile(MediaException.InvalidFileException ex) {
        return invalidFile(ex);
    }

    /** Spring corta la subida antes del controlador si supera spring.servlet.multipart.max-file-size. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return invalidFile(new MediaException.FileTooLargeException(maxFileSize));
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

    private static ResponseEntity<ErrorResponse> invalidFile(MediaException.InvalidFileException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage(),
                        Map.of("file", ex.getDetail())));
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, error, message, null));
    }

    /** Schema ErrorResponse de openapi.yaml (ADR-003); {@code details} solo en validación. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String error, String message, int status, String timestamp,
                                Map<String, String> details) {

        static ErrorResponse of(HttpStatus status, String error, String message, Map<String, String> details) {
            return new ErrorResponse(error, message, status.value(),
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), details);
        }
    }
}
