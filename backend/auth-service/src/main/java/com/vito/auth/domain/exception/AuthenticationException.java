package com.vito.auth.domain.exception;

public class AuthenticationException extends RuntimeException {
    
    public AuthenticationException(String message) {
        super(message);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    // Mensajes en es-ES: el frontend los muestra tal cual (CONVENTIONS §3.3)

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException("Credenciales inválidas");
    }
    
    public static AuthenticationException userNotFound() {
        return new AuthenticationException("Usuario no encontrado");
    }
    
    /** Access token ausente, mal formado, de otro tipo o expirado (logoutUser 401). */
    public static AuthenticationException invalidAccessToken() {
        return new AuthenticationException("El token de acceso es inválido o ha expirado");
    }
    
    /** Refresh token mal formado, expirado, revocado o de un usuario inexistente (refreshToken 401). */
    public static AuthenticationException invalidRefreshToken() {
        return new AuthenticationException("El refresh token es inválido o ha expirado");
    }
}
