package com.vito.social.domain.port.inbound;

import com.vito.social.domain.model.SocialProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public interface SocialService {
    
    void follow(FollowCommand command);
    
    void unfollow(UnfollowCommand command);
    
    boolean isFollowing(String followerId, String followingId);
    
    SocialProfile getSocialProfile(String userId, String currentUserId);
    
    List<SocialProfile> getFollowers(String userId, int page, int size);
    
    List<SocialProfile> getFollowing(String userId, int page, int size);
    
    List<SocialProfile> searchUsers(String query, int page, int size);
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class FollowCommand {
        private String followerId;
        private String followingId;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class UnfollowCommand {
        private String followerId;
        private String followingId;
    }
}
