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
    
    long countByFollowerId(String followerId);
    
    long countByFollowingId(String followingId);
    
    /**
     * T051 (research R7): ids de quienes siguen a {@code userId}, del más reciente al más antiguo
     * ({@code createdAt} y {@code _id} descendentes), paginados en la base. {@code page} base 0.
     */
    List<String> findFollowerIds(String userId, int page, int size);

    /** T051 (research R7): ids de a quienes sigue {@code userId}, mismo orden y paginación. */
    List<String> findFollowingIds(String userId, int page, int size);
}
