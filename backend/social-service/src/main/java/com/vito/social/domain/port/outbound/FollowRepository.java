package com.vito.social.domain.port.outbound;

import com.vito.social.domain.model.FollowRelationship;

import java.util.List;

public interface FollowRepository {
    
    /**
     * T039 (research R5): crea la relación si no existe. El índice único del par lo hace atómico
     * con peticiones simultáneas.
     *
     * @return {@code true} si la creó; {@code false} si ya existía (no es error)
     */
    boolean insertIfAbsent(FollowRelationship relationship);

    /**
     * T039 (research R5): borra la relación del par si existe.
     *
     * @return {@code true} si borró algo; {@code false} si no existía (no es error)
     */
    boolean deleteIfPresent(String followerId, String followingId);
    
    boolean existsByFollowerIdAndFollowingId(String followerId, String followingId);
    
    List<FollowRelationship> findByFollowerId(String followerId, int page, int size);
    
    List<FollowRelationship> findByFollowingId(String followingId, int page, int size);
    
    long countByFollowerId(String followerId);
    
    long countByFollowingId(String followingId);
    
    List<String> findFollowerIdsByFollowingId(String followingId);
    
    List<String> findFollowingIdsByFollowerId(String followerId);
}
