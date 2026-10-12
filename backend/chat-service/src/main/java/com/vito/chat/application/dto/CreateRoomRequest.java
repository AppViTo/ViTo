package com.vito.chat.application.dto;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

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
