package com.vito.chat.application.dto;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record SendMessageRequest(
        @NotBlank(message = "Room ID is required")
        String roomId,
        
        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Message content cannot exceed 5000 characters")
        String content,
        
        ChatMessage.MessageType type,
        
        String mediaUrl
) {
    public ChatMessage toMessage(String senderId, String senderName) {
        return ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .senderName(senderName)
                .content(content)
                .type(type != null ? type : ChatMessage.MessageType.TEXT)
                .mediaUrl(mediaUrl)
                .build();
    }
}
