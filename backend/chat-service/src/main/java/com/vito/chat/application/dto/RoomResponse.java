package com.vito.chat.application.dto;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

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
