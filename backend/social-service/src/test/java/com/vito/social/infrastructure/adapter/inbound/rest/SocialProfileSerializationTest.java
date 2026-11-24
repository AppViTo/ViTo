package com.vito.social.infrastructure.adapter.inbound.rest;

import com.vito.social.domain.model.SocialProfile;
import com.vito.social.infrastructure.adapter.inbound.rest.dto.SocialProfileResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T043 (Ola 2a, T-B13): el JSON del perfil trae {@code isFollowing} e {@code isFollowedBy} (no
 * {@code following} ni {@code followedBy}, que es lo que Jackson saca de un {@code boolean is*})
 * y coincide campo por campo con los fixtures {@code getSocialProfile.*.200.json}.
 */
class SocialProfileSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void otherProfileMatchesFixture() throws Exception {
        SocialProfile profile = SocialProfile.builder()
                .userId("usr_01H8X2K4QV")
                .username("caro.mz")
                .displayName("Carolina Martínez")
                .avatar("https://cdn.vito.com/usr_01H8X2K4QV/avatar.jpg")
                .followersCount(2)
                .followingCount(1)
                .isFollowing(true)
                .isFollowedBy(true)
                .build();

        assertEquals(fixture("getSocialProfile.other.200.json"), serialize(profile));
    }

    @Test
    void ownProfileMatchesFixture() throws Exception {
        SocialProfile profile = SocialProfile.builder()
                .userId("usr_01H8X2K4QJ")
                .username("alex_villa")
                .displayName("Alex Villa")
                .avatar("https://cdn.vito.com/usr_01H8X2K4QJ/avatar.jpg")
                .followersCount(1)
                .followingCount(2)
                .build();

        assertEquals(fixture("getSocialProfile.own.200.json"), serialize(profile));
    }

    @Test
    void booleansUseTheContractNamesAndNoLegacyFieldsLeak() throws Exception {
        JsonNode json = serialize(SocialProfile.builder().userId("u").username("u").isFollowing(true).build());

        assertTrue(json.get("isFollowing").asBoolean());
        assertFalse(json.has("following"));
        assertFalse(json.has("followedBy"));
        assertFalse(json.has("bio"));
    }

    /** {@code displayName} y {@code avatar} son obligatorios en el schema aunque valgan null. */
    @Test
    void nullDisplayNameAndAvatarAreSentAsNull() throws Exception {
        JsonNode json = serialize(SocialProfile.builder().userId("u").username("u").build());

        assertTrue(json.has("displayName") && json.get("displayName").isNull());
        assertTrue(json.has("avatar") && json.get("avatar").isNull());
    }

    private JsonNode serialize(SocialProfile profile) {
        return mapper.readTree(mapper.writeValueAsString(SocialProfileResponse.from(profile)));
    }

    private JsonNode fixture(String name) throws Exception {
        return mapper.readTree(Files.readString(Path.of("..", "..", "docs", "api", "fixtures", "social", name)));
    }
}
