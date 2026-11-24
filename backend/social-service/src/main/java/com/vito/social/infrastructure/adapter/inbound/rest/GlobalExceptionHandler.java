package com.vito.social.infrastructure.adapter.inbound.rest;

import static com.vito.shared.web.error.ErrorCodes.SERVICE_UNAVAILABLE;

import com.vito.social.domain.exception.SocialException;
import com.vito.social.domain.exception.UserDirectoryUnavailableException;
import com.vito.shared.web.error.BaseExceptionHandler;
import com.vito.shared.web.error.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Envelope único de error de ADR-003: los handlers genéricos vienen de
 * {@link BaseExceptionHandler} (shared-web, T-B18); aquí solo las excepciones de dominio.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    /** Segundos que se sugiere esperar cuando auth-service no responde (research R2). */
    static final String RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler(SocialException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(SocialException ex) {
        return domainError(ex.getReason(), ex.getMessage(), ex.getDetails());
    }

    /** T019 (FR-012a): sin datos de identidad no hay respuesta a medias; 503 y reintentar. */
    @ExceptionHandler(UserDirectoryUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUserDirectoryUnavailable(UserDirectoryUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE, ex.getMessage(), null));
    }
}
