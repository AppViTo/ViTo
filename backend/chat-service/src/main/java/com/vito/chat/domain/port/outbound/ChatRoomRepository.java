package com.vito.chat.domain.port.outbound;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository {

    ChatRoom save(ChatRoom room);
    Optional<ChatRoom> findById(String id);
    List<ChatRoom> findByEventId(String eventId);
    List<ChatRoom> findByParticipantId(String userId);
    void deleteById(String id);
}
