package com.vito.social.domain.exception;

import java.util.Map;

/**
 * Error de dominio de social-service. {@link Reason} dice qué clase de error es y el
 * GlobalExceptionHandler lo traduce a status y código de ADR-003 (T-B03).
 */
public class SocialException extends RuntimeException {

    public enum Reason { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Reason reason;
    private final Map<String, String> details;

    public SocialException(Reason reason, String message) {
        this(reason, message, null);
    }

    public SocialException(Reason reason, String message, Map<String, String> details) {
        super(message);
        this.reason = reason;
        this.details = details;
    }

    public Reason getReason() {
        return reason;
    }

    /** Detalle por campo de un {@code VALIDATION_ERROR} (ADR-003); null si no aplica. */
    public Map<String, String> getDetails() {
        return details;
    }

    // Mensajes en es-ES: el frontend los muestra tal cual (CONVENTIONS §3.3)

    /** Sin usuario autenticado en el contexto de seguridad. */
    public static SocialException unauthenticated() {
        return new SocialException(Reason.UNAUTHORIZED, "Debes iniciar sesión");
    }

    /** auth-service rechazó el token reenviado: venció entre el gateway y aquí (research R2). */
    public static SocialException sessionExpired() {
        return new SocialException(Reason.UNAUTHORIZED, "El token de acceso es inválido o ha expirado");
    }

    /** {@code listFollowers}/{@code listFollowing}: mismo texto que sus fixtures {@code *.400.json}. */
    public static SocialException invalidPagination(String field, String detail) {
        return new SocialException(Reason.INVALID, "La paginación no es válida", Map.of(field, detail));
    }

    public static SocialException cannotFollowYourself() {
        return new SocialException(Reason.INVALID, "No puedes seguirte a ti mismo");
    }

    public static SocialException userNotFound() {
        return new SocialException(Reason.NOT_FOUND, "El usuario no existe");
    }

    public static SocialException cannotFollowPrivateUser() {
        return new SocialException(Reason.FORBIDDEN, "No puedes seguir a un usuario privado sin su aprobación");
    }
}
