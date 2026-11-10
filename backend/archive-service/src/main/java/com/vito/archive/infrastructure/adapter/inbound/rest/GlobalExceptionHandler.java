package com.vito.archive.infrastructure.adapter.inbound.rest;

import com.vito.archive.domain.exception.ArchiveException;
import com.vito.shared.web.error.BaseExceptionHandler;
import com.vito.shared.web.error.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Envelope único de error de ADR-003: los handlers genéricos vienen de
 * {@link BaseExceptionHandler} (shared-web, T-B18); aquí solo las excepciones de dominio.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ArchiveException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(ArchiveException ex) {
        return domainError(ex.getReason(), ex.getMessage(), null);
    }
}
