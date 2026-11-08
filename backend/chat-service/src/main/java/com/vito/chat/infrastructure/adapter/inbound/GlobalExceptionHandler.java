package com.vito.chat.infrastructure.adapter.inbound;

import static com.vito.shared.web.error.ErrorCodes.FORBIDDEN;
import static com.vito.shared.web.error.ErrorCodes.NOT_FOUND;
import static com.vito.shared.web.error.ErrorCodes.VALIDATION_ERROR;

import com.vito.chat.domain.exception.ChatException;
import com.vito.shared.web.error.BaseExceptionHandler;
import com.vito.shared.web.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Envelope único de error de ADR-003: los handlers genéricos vienen de
 * {@link BaseExceptionHandler} (shared-web, T-B18); aquí solo las excepciones de chat.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ChatException.RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(ChatException.RoomNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ChatException.MessageNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotFound(ChatException.MessageNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ChatException.UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(ChatException.UnauthorizedException ex) {
        return error(HttpStatus.FORBIDDEN, FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ChatException.RoomFullException.class)
    public ResponseEntity<ErrorResponse> handleRoomFull(ChatException.RoomFullException ex) {
        return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage());
    }
}
