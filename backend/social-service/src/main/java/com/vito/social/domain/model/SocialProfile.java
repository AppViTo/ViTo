package com.vito.social.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    
    public void incrementFollowers() {
        this.followersCount++;
    }
    
    public void decrementFollowers() {
        if (this.followersCount > 0) {
            this.followersCount--;
        }
    }
    
    public void incrementFollowing() {
        this.followingCount++;
    }
    
    public void decrementFollowing() {
        if (this.followingCount > 0) {
            this.followingCount--;
        }
    }
}
