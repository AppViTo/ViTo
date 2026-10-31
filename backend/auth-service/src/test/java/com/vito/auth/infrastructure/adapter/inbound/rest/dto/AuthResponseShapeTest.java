package com.vito.auth.infrastructure.adapter.inbound.rest.dto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.vito.auth.domain.model.User;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-B16: forma de la respuesta de register/login/refresh fijada por ADR-004 (manda sobre
 * openapi.yaml, que se corrige en T-C08): {@code user} plano y sin {@code tokenType}.
 */
class AuthResponseShapeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void authResponseHasTheAdr004Shape() {
        User user = User.builder()
                .id("usr_1").username("alex_villa").email("alex@vito.com")
                .profile(User.Profile.builder().displayName("Alex Villa").avatar("https://cdn/a.jpg").bio("bio").build())
                .stats(User.Stats.builder().followersCount(128L).build())
                .build();
        AuthResponse response = AuthResponse.builder()
                .accessToken("access").refreshToken("refresh").expiresIn(86400L)
                .user(AuthResponse.UserInfo.fromUser(user))
                .build();

        JsonNode json = mapper.valueToTree(response);

        assertEquals(Set.of("accessToken", "refreshToken", "expiresIn", "user"), fieldNames(json));
        assertEquals(Set.of("id", "username", "email", "displayName", "avatar"), fieldNames(json.get("user")));
        assertEquals("Alex Villa", json.get("user").get("displayName").asText());
        assertEquals("https://cdn/a.jpg", json.get("user").get("avatar").asText());
        assertEquals(86400, json.get("expiresIn").asLong());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
