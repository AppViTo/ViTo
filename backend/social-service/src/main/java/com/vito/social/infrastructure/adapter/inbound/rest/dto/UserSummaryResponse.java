package com.vito.social.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.vito.social.domain.model.UserSummary;

/**
 * T052 (Ola 2a): schema {@code UserSummary} de openapi.yaml, una fila de {@code listFollowers} y
 * {@code listFollowing}. {@code displayName} y {@code avatar} son obligatorios y nullable: se
 * envían siempre. Nunca correo ni teléfono.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"userId", "username", "displayName", "avatar"})
public record UserSummaryResponse(String userId, String username, String displayName, String avatar) {

    public static UserSummaryResponse from(UserSummary user) {
        return new UserSummaryResponse(user.userId(), user.username(), user.displayName(), user.avatar());
    }
}
