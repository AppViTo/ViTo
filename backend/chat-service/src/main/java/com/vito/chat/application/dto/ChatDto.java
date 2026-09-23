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

public record CreateRoomRequest(
        @NotBlank(message = "Event ID is required")
        String eventId,
        
        String name,
        
        ChatRoom.RoomType type,
        
        List<String> participantIds
) {
    public ChatRoom toRoom(String createdBy) {
        return ChatRoom.builder()
                .eventId(eventId)
                .name(name)
                .type(type != null ? type : ChatRoom.RoomType.EVENT_CHAT)
                .participantIds(participantIds != null ? participantIds : List.of())
                .createdBy(createdBy)
                .build();
    }
}

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

public record RoomResponse(
        String id,
        String eventId,
        String name,
        ChatRoom.RoomType type,
        List<String> participantIds,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt,
        int unreadCount
) {
    public static RoomResponse from(ChatRoom room) {
        return new RoomResponse(
                room.getId(),
                room.getEventId(),
                room.getName(),
                room.getType(),
                room.getParticipantIds(),
                room.getCreatedBy(),
                room.getCreatedAt(),
                room.getLastMessageAt(),
                room.getUnreadCount()
        );
    }
}

public record TypingIndicatorRequest(
        @NotBlank(message = "Room ID is required")
        String roomId,
        
        boolean isTyping
) {}
