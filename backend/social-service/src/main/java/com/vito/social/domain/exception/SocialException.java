package com.vito.social.domain.exception;

/**
 * Error de dominio de social-service. {@link Reason} dice qué clase de error es y el
 * GlobalExceptionHandler lo traduce a status y código de ADR-003 (T-B03).
 */
public class SocialException extends RuntimeException {

    public enum Reason { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Reason reason;

    public SocialException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    // Mensajes en es-ES: el frontend los muestra tal cual (CONVENTIONS §3.3)

    /** Sin usuario autenticado en el contexto de seguridad. */
    public static SocialException unauthenticated() {
        return new SocialException(Reason.UNAUTHORIZED, "Debes iniciar sesión");
    }

    public static SocialException cannotFollowYourself() {
        return new SocialException(Reason.INVALID, "No puedes seguirte a ti mismo");
    }

    public static SocialException alreadyFollowing() {
        return new SocialException(Reason.CONFLICT, "Ya sigues a este usuario");
    }

    public static SocialException notFollowing() {
        return new SocialException(Reason.CONFLICT, "No sigues a este usuario");
    }

    public static SocialException userNotFound() {
        return new SocialException(Reason.NOT_FOUND, "El usuario no existe");
    }

    public static SocialException cannotFollowPrivateUser() {
        return new SocialException(Reason.FORBIDDEN, "No puedes seguir a un usuario privado sin su aprobación");
    }
}
