package com.vito.mosaic.infrastructure.adapter.inbound.rest;

import com.vito.mosaic.domain.exception.MosaicException;
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

    @ExceptionHandler(MosaicException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(MosaicException ex) {
        return domainError(ex.getReason(), ex.getMessage(), null);
    }
}
