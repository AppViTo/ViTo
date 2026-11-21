package com.vito.auth.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vito.auth.domain.model.UserSummary;

/**
 * Schema {@code UserSummary} de openapi.yaml: las cuatro claves siempre presentes, con
 * {@code displayName} y {@code avatar} en null si faltan. Nunca correo ni teléfono.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserSummaryResponse(String userId, String username, String displayName, String avatar) {

    public static UserSummaryResponse from(UserSummary user) {
        return new UserSummaryResponse(user.userId(), user.username(), user.displayName(), user.avatar());
    }
}
