package com.vito.chat.domain.port.outbound;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;

import java.util.List;
import java.util.Optional;

public interface ChatEventPublisher {

    void publishMessage(ChatMessage message);
    void publishTypingIndicator(String roomId, String userId, boolean isTyping);
    void publishUserJoined(String roomId, String userId);
    void publishUserLeft(String roomId, String userId);
}
