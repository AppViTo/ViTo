package com.vito.auth.infrastructure.adapter.inbound.rest;

import static com.vito.shared.web.error.ErrorCodes.CONFLICT;
import static com.vito.shared.web.error.ErrorCodes.FORBIDDEN;
import static com.vito.shared.web.error.ErrorCodes.UNAUTHORIZED;

import com.vito.auth.domain.exception.AuthenticationException;
import com.vito.auth.domain.exception.AuthorizationException;
import com.vito.auth.domain.exception.UserAlreadyExistsException;
import com.vito.shared.web.error.BaseExceptionHandler;
import com.vito.shared.web.error.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Envelope único de error de ADR-003: los handlers genéricos vienen de
 * {@link BaseExceptionHandler} (shared-web, T-B18); aquí solo las excepciones de auth.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

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

    /** Sin header Authorization en logout: mismo 401 que un token inválido (logoutUser 401). */
    @Override
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(ex.getHeaderName())) {
            return handleAuthenticationException(AuthenticationException.invalidAccessToken());
        }
        return super.handleMissingHeader(ex);
    }
}
