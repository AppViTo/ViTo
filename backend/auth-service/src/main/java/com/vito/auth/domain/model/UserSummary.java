package com.vito.auth.domain.model;

/**
 * Datos públicos de un usuario (schema {@code UserSummary} de openapi.yaml, Ola 2a). Nunca
 * correo ni teléfono. {@code displayName} y {@code avatar} pueden ser null.
 */
public record UserSummary(String userId, String username, String displayName, String avatar) {

    public static UserSummary from(User user) {
        User.Profile profile = user.getProfile();
        return new UserSummary(user.getId(), user.getUsername(),
                profile != null ? profile.getDisplayName() : null,
                profile != null ? profile.getAvatar() : null);
    }
}
