package com.vito.chat.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_rooms")
public class ChatRoom {

    @Id
    private String id;

    @Indexed
    private String eventId;

    private String name;

    private RoomType type;

    private List<String> participantIds;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime lastMessageAt;

    private int unreadCount;

    public enum RoomType {
        EVENT_CHAT,
        PRIVATE,
        GROUP
    }
}
