package com.vito.social.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.vito.social.domain.model.SocialProfile;

/**
 * T046 (Ola 2a): schema {@code SocialProfile} de openapi.yaml. Los nombres de los booleanos van
 * explícitos porque Jackson quita el {@code is} de un {@code boolean isFollowing} (T-B13).
 * {@code displayName} y {@code avatar} son obligatorios y nullable: se envían siempre.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"userId", "username", "displayName", "avatar", "followersCount", "followingCount",
        "isFollowing", "isFollowedBy"})
public record SocialProfileResponse(
        String userId,
        String username,
        String displayName,
        String avatar,
        long followersCount,
        long followingCount,
        @JsonProperty("isFollowing") boolean isFollowing,
        @JsonProperty("isFollowedBy") boolean isFollowedBy) {

    public static SocialProfileResponse from(SocialProfile profile) {
        return new SocialProfileResponse(profile.getUserId(), profile.getUsername(), profile.getDisplayName(),
                profile.getAvatar(), profile.getFollowersCount(), profile.getFollowingCount(),
                profile.isFollowing(), profile.isFollowedBy());
    }
}
