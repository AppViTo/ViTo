package com.vito.auth.domain.exception;

public class AuthenticationException extends RuntimeException {
    
    public AuthenticationException(String message) {
        super(message);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException("Invalid email or password");
    }
    
    public static AuthenticationException userNotFound() {
        return new AuthenticationException("User not found");
    }
    
    public static AuthenticationException tokenExpired() {
        return new AuthenticationException("Token has expired");
    }
    
    public static AuthenticationException invalidToken() {
        return new AuthenticationException("Invalid token");
    }
}
