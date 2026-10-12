package com.vito.chat.domain.port.outbound;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;

import java.util.List;
import java.util.Optional;

public interface CachePort {

    void publishMessage(String roomId, ChatMessage message);
    void publishTypingIndicator(String roomId, String userId, boolean isTyping);
    void subscribeToRoom(String roomId, String userId);
    void unsubscribeFromRoom(String roomId, String userId);
    List<String> getOnlineUsers(String roomId);
}
