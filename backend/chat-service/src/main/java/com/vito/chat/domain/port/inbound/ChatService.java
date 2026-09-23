package com.vito.chat.domain.port.inbound;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;

import java.util.List;
import java.util.Optional;

public interface ChatService {

    // Room operations
    ChatRoom createRoom(ChatRoom room);
    Optional<ChatRoom> getRoomById(String roomId);
    List<ChatRoom> getRoomsByEventId(String eventId);
    List<ChatRoom> getRoomsByUserId(String userId);
    ChatRoom joinRoom(String roomId, String userId);
    ChatRoom leaveRoom(String roomId, String userId);

    // Message operations
    ChatMessage sendMessage(ChatMessage message);
    List<ChatMessage> getMessagesByRoom(String roomId, int page, int size);
    Optional<ChatMessage> getMessageById(String messageId);
    ChatMessage editMessage(String messageId, String content, String userId);
    void deleteMessage(String messageId, String userId);
    void markMessagesAsRead(String roomId, String userId);

    // Typing indicators
    void sendTypingIndicator(String roomId, String userId, boolean isTyping);
}
