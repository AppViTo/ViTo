package com.vito.chat.domain.port.outbound;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);
    Optional<ChatMessage> findById(String id);
    List<ChatMessage> findByRoomId(String roomId, int page, int size);
    long countByRoomId(String roomId);
    void deleteById(String id);
    void markAsRead(String roomId, String userId);
}
