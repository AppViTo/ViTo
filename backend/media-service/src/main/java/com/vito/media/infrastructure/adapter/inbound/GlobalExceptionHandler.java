package com.vito.media.infrastructure.adapter.inbound;

import static com.vito.shared.web.error.ErrorCodes.FORBIDDEN;
import static com.vito.shared.web.error.ErrorCodes.INTERNAL_ERROR;
import static com.vito.shared.web.error.ErrorCodes.NOT_FOUND;
import static com.vito.shared.web.error.ErrorCodes.VALIDATION_ERROR;

import com.vito.media.domain.exception.MediaException;
import com.vito.shared.web.error.BaseExceptionHandler;
import com.vito.shared.web.error.ErrorResponse;
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
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

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

    private static ResponseEntity<ErrorResponse> invalidFile(MediaException.InvalidFileException ex) {
        return validationError(ex.getMessage(), Map.of("file", ex.getDetail()));
    }
}
