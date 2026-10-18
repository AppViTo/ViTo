package com.vito.auth.domain.exception;

/** Registro con un email o username que ya existe: 409 CONFLICT (ADR-009). */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }

    public static UserAlreadyExistsException emailTaken() {
        return new UserAlreadyExistsException("El email ya está registrado");
    }

    public static UserAlreadyExistsException usernameTaken() {
        return new UserAlreadyExistsException("El nombre de usuario ya está en uso");
    }
}
