package com.vito.social.domain.port.outbound;

import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.domain.model.SocialProfile;

import java.util.List;

public interface FollowRepository {
    
    FollowRelationship save(FollowRelationship relationship);
    
    void delete(FollowRelationship relationship);
    
    boolean existsByFollowerIdAndFollowingId(String followerId, String followingId);
    
    List<FollowRelationship> findByFollowerId(String followerId, int page, int size);
    
    List<FollowRelationship> findByFollowingId(String followingId, int page, int size);
    
    long countByFollowerId(String followerId);
    
    long countByFollowingId(String followingId);
    
    List<String> findFollowerIdsByFollowingId(String followingId);
    
    List<String> findFollowingIdsByFollowerId(String followerId);
}
