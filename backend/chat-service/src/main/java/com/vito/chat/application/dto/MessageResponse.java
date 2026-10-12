package com.vito.chat.application.dto;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        String id,
        String roomId,
        String senderId,
        String senderName,
        String content,
        ChatMessage.MessageType type,
        String mediaUrl,
        LocalDateTime timestamp,
        LocalDateTime editedAt,
        boolean deleted
) {
    public static MessageResponse from(ChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getSenderId(),
                message.getSenderName(),
                message.getContent(),
                message.getType(),
                message.getMediaUrl(),
                message.getTimestamp(),
                message.getEditedAt(),
                message.isDeleted()
        );
    }
}
