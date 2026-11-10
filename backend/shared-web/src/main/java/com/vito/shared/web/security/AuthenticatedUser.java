package com.vito.shared.web.security;

import java.security.Principal;

/**
 * Quien hace la petición: el {@code sub} del token y su nombre visible (R6). {@link #getName()}
 * devuelve el id, así {@code Authentication.getName()} sigue siendo el userId.
 *
 * @param displayName claim {@code name} o, si falta, {@code X-User-Name} del gateway; null si no hay
 */
public record AuthenticatedUser(String id, String displayName) implements Principal {

    /** Mismo límite que el nombre del perfil; un valor más largo se recorta. */
    public static final int MAX_DISPLAY_NAME = 100;

    public AuthenticatedUser {
        displayName = normalize(displayName);
    }

    @Override
    public String getName() {
        return id;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() > MAX_DISPLAY_NAME ? trimmed.substring(0, MAX_DISPLAY_NAME) : trimmed;
    }
}
