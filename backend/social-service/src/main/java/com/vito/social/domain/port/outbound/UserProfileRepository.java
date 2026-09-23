package com.vito.social.domain.port.outbound;

import com.vito.social.domain.model.SocialProfile;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository {
    
    Optional<SocialProfile> findByUserId(String userId);
    
    List<SocialProfile> findByUserIds(List<String> userIds);
    
    SocialProfile save(SocialProfile profile);
    
    void incrementFollowersCount(String userId);
    
    void decrementFollowersCount(String userId);
    
    void incrementFollowingCount(String userId);
    
    void decrementFollowingCount(String userId);
}
