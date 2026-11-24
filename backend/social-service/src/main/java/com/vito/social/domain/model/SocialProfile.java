package com.vito.social.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Perfil social visto por quien consulta (vista {@code SocialProfile} de data-model.md, Ola 2a).
 * La identidad viene de auth-service y los contadores se calculan con {@code count} sobre el grafo
 * (research R6): no se guardan ni se incrementan. {@code bio} solo lo usan aún las listas, que
 * cambian a {@code UserSummary} en US4.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialProfile {
    
    private String userId;
    
    private String username;
    
    private String displayName;
    
    private String avatar;
    
    private String bio;
    
    private int followersCount;
    
    private int followingCount;
    
    private boolean isFollowing;
    
    private boolean isFollowedBy;
}
