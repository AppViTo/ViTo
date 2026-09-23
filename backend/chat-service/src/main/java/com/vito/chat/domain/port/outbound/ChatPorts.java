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

public interface ChatRoomRepository {

    ChatRoom save(ChatRoom room);
    Optional<ChatRoom> findById(String id);
    List<ChatRoom> findByEventId(String eventId);
    List<ChatRoom> findByParticipantId(String userId);
    void deleteById(String id);
}

public interface ChatEventPublisher {

    void publishMessage(ChatMessage message);
    void publishTypingIndicator(String roomId, String userId, boolean isTyping);
    void publishUserJoined(String roomId, String userId);
    void publishUserLeft(String roomId, String userId);
}

public interface CachePort {

    void publishMessage(String roomId, ChatMessage message);
    void publishTypingIndicator(String roomId, String userId, boolean isTyping);
    void subscribeToRoom(String roomId, String userId);
    void unsubscribeFromRoom(String roomId, String userId);
    List<String> getOnlineUsers(String roomId);
}
