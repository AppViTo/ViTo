package com.vito.social.domain.port.inbound;

import com.vito.social.domain.model.SocialProfile;
import com.vito.social.domain.model.UserSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public interface SocialService {
    
    void follow(FollowCommand command);
    
    void unfollow(UnfollowCommand command);
    
    SocialProfile getSocialProfile(String userId, String currentUserId);
    
    List<UserSummary> getFollowers(String userId, int page, int size);
    
    List<UserSummary> getFollowing(String userId, int page, int size);
    
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
