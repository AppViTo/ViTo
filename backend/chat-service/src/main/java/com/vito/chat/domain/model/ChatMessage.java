package com.vito.chat.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_messages")
@CompoundIndexes({
    @CompoundIndex(name = "room_timestamp_idx", def = "{'roomId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "sender_room_idx", def = "{'senderId': 1, 'roomId': 1}")
})
public class ChatMessage {

    @Id
    private String id;

    @Indexed
    private String roomId;

    @Indexed
    private String senderId;

    private String senderName;

    private String content;

    private MessageType type;

    private String mediaUrl;

    private LocalDateTime timestamp;

    private LocalDateTime editedAt;

    private boolean deleted;

    public enum MessageType {
        TEXT,
        IMAGE,
        VIDEO,
        AUDIO,
        FILE,
        SYSTEM,
        TYPING,
        READ_RECEIPT
    }
}
