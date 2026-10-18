package com.vito.auth.domain.exception;

public class AuthorizationException extends RuntimeException {
    
    public AuthorizationException(String message) {
        super(message);
    }
    
    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static AuthorizationException insufficientPermissions() {
        return new AuthorizationException("No tienes permisos para realizar esta acción");
    }
    
    public static AuthorizationException accessDenied() {
        return new AuthorizationException("Acceso denegado");
    }
}
