package com.vito.social.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowRelationship {
    
    private String id;
    
    private String followerId;
    
    private String followingId;
    
    private LocalDateTime createdAt;
    
    private boolean notificationsEnabled;
    
    public static FollowRelationship create(String followerId, String followingId) {
        return FollowRelationship.builder()
                .followerId(followerId)
                .followingId(followingId)
                .createdAt(LocalDateTime.now())
                .notificationsEnabled(true)
                .build();
    }
}
