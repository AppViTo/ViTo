package com.vito.streaming.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reaction {
    
    private String id;
    
    private String streamId;
    
    private String userId;
    
    private String username;
    
    private String displayName;
    
    private String avatar;
    
    private ReactionType type;
    
    private String emoji;
    
    private LocalDateTime timestamp;
    
    private double x;
    
    private double y;
    
    public enum ReactionType {
        LIKE,
        HEART,
        FIRE,
        CLAP,
        CELEBRATION,
        CUSTOM
    }
    
    public static Reaction createLike(String streamId, String userId, String username, 
                                      String displayName, String avatar) {
        return Reaction.builder()
                .streamId(streamId)
                .userId(userId)
                .username(username)
                .displayName(displayName)
                .avatar(avatar)
                .type(ReactionType.LIKE)
                .emoji("👍")
                .timestamp(LocalDateTime.now())
                .x(Math.random())
                .y(Math.random())
                .build();
    }
    
    public static Reaction createHeart(String streamId, String userId, String username,
                                       String displayName, String avatar) {
        return Reaction.builder()
                .streamId(streamId)
                .userId(userId)
                .username(username)
                .displayName(displayName)
                .avatar(avatar)
                .type(ReactionType.HEART)
                .emoji("❤️")
                .timestamp(LocalDateTime.now())
                .x(Math.random())
                .y(Math.random())
                .build();
    }
    
    public static Reaction createFire(String streamId, String userId, String username,
                                      String displayName, String avatar) {
        return Reaction.builder()
                .streamId(streamId)
                .userId(userId)
                .username(username)
                .displayName(displayName)
                .avatar(avatar)
                .type(ReactionType.FIRE)
                .emoji("🔥")
                .timestamp(LocalDateTime.now())
                .x(Math.random())
                .y(Math.random())
                .build();
    }
}
