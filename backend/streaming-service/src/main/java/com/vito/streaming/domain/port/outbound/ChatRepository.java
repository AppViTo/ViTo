package com.vito.streaming.domain.port.outbound;

import com.vito.streaming.domain.model.ChatMessage;

import java.util.List;

public interface ChatRepository {
    
    ChatMessage save(ChatMessage message);
    
    List<ChatMessage> findByStreamId(String streamId, int page, int size);
    
    long countByStreamId(String streamId);
    
    void delete(String messageId);
}
