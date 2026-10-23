package com.vito.media.infrastructure.adapter.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vito.media.domain.exception.MediaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    @Value("${media.max-file-size:10485760}")
    private long maxFileSize;

    @ExceptionHandler(MediaException.MediaNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMediaNotFound(MediaException.MediaNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "Media not found",
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    @ExceptionHandler(MediaException.UploadFailedException.class)
    public ResponseEntity<Map<String, Object>> handleUploadFailed(MediaException.UploadFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Upload failed",
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    @ExceptionHandler(MediaException.ProcessingFailedException.class)
    public ResponseEntity<Map<String, Object>> handleProcessingFailed(MediaException.ProcessingFailedException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Processing failed",
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    @ExceptionHandler(MediaException.UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(MediaException.UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "Unauthorized",
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                ));
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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage(),
                        Map.of("file", ex.getDetail())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal server error",
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    /** Schema ErrorResponse de openapi.yaml (ADR-003); {@code details} solo en validación. Copia temporal (ADR-017). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String error, String message, int status, String timestamp,
                                Map<String, String> details) {

        static ErrorResponse of(HttpStatus status, String error, String message, Map<String, String> details) {
            return new ErrorResponse(error, message, status.value(),
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), details);
        }
    }
}
