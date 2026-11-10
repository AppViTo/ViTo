package com.vito.media.infrastructure.adapter.inbound;

import static com.vito.shared.web.error.ErrorCodes.FORBIDDEN;
import static com.vito.shared.web.error.ErrorCodes.INTERNAL_ERROR;
import static com.vito.shared.web.error.ErrorCodes.NOT_FOUND;
import static com.vito.shared.web.error.ErrorCodes.UNAUTHORIZED;
import static com.vito.shared.web.error.ErrorCodes.VALIDATION_ERROR;

import com.vito.media.domain.exception.MediaException;
import com.vito.shared.web.error.BaseExceptionHandler;
import com.vito.shared.web.error.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Envelope único de error de ADR-003: los handlers genéricos vienen de
 * {@link BaseExceptionHandler} (shared-web, T-B18); aquí solo las excepciones de media.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    /** T-B33: event-service no responde (shared-web no tiene este código). */
    static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    @Value("${media.max-file-size:10485760}")
    private long maxFileSize;

    @ExceptionHandler(MediaException.MediaNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMediaNotFound(MediaException.MediaNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MediaException.EventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEventNotFound(MediaException.EventNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MediaException.NotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleNotAuthenticated(MediaException.NotAuthenticatedException ex) {
        return error(HttpStatus.UNAUTHORIZED, UNAUTHORIZED, ex.getMessage());
    }

    /** T-B33: sin event-service no se puede decidir el acceso; el cliente reintenta. */
    @ExceptionHandler(MediaException.DependencyUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDependencyUnavailable(MediaException.DependencyUnavailableException ex) {
        log.warn("event-service unavailable: {}", ex.getCause() != null ? ex.getCause().toString() : "-");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE, ex.getMessage(), null));
    }

    /** page, size o expiresIn fuera de rango (T-C09). */
    @ExceptionHandler(MediaException.InvalidParameterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(MediaException.InvalidParameterException ex) {
        return validationError(ex.getMessage(), Map.of(ex.getParameter(), ex.getDetail()));
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

    private static ResponseEntity<ErrorResponse> invalidFile(MediaException.InvalidFileException ex) {
        return validationError(ex.getMessage(), Map.of("file", ex.getDetail()));
    }
}
