package com.vito.event.infrastructure.adapter.inbound.rest;

import com.vito.event.domain.exception.EventException;
import com.vito.event.infrastructure.config.InviteLinkTokenRedaction;
import com.vito.shared.web.error.BaseExceptionHandler;
import com.vito.shared.web.error.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Envelope único de error de ADR-003: los handlers genéricos vienen de
 * {@link BaseExceptionHandler} (shared-web, T-B18); aquí solo {@link EventException}, con sus
 * {@code details} de validación, y la redacción del token del enlace en los logs (T067).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(EventException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(EventException ex) {
        return domainError(ex.getReason(), ex.getMessage(), ex.getDetails());
    }

    @Override
    protected String redact(String message) {
        return InviteLinkTokenRedaction.redact(message);
    }
}
