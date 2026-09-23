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
public class ChatMessage {
    
    private String id;
    
    private String streamId;
    
    private String userId;
    
    private String username;
    
    private String displayName;
    
    private String avatar;
    
    private String message;
    
    private MessageType type;
    
    private LocalDateTime timestamp;
    
    private boolean isDeleted;
    
    public enum MessageType {
        NORMAL,
        SUPER_CHAT,
        SYSTEM,
        JOIN,
        LEAVE
    }
    
    public static ChatMessage createNormal(String streamId, String userId, String username,
                                           String displayName, String avatar, String message) {
        return ChatMessage.builder()
                .streamId(streamId)
                .userId(userId)
                .username(username)
                .displayName(displayName)
                .avatar(avatar)
                .message(message)
                .type(MessageType.NORMAL)
                .timestamp(LocalDateTime.now())
                .isDeleted(false)
                .build();
    }
    
    public static ChatMessage createSystem(String streamId, String message) {
        return ChatMessage.builder()
                .streamId(streamId)
                .userId("system")
                .username("System")
                .displayName("System")
                .message(message)
                .type(MessageType.SYSTEM)
                .timestamp(LocalDateTime.now())
                .isDeleted(false)
                .build();
    }
    
    public static ChatMessage createJoin(String streamId, String userId, String username,
                                         String displayName) {
        return ChatMessage.builder()
                .streamId(streamId)
                .userId(userId)
                .username(username)
                .displayName(displayName)
                .message("$displayName joined the stream")
                .type(MessageType.JOIN)
                .timestamp(LocalDateTime.now())
                .isDeleted(false)
                .build();
    }
}
