package com.vito.streaming.domain.port.outbound;

import com.vito.streaming.domain.model.ChatMessage;
import com.vito.streaming.domain.model.Reaction;

public interface StreamingEventPublisher {
    
    void publishStreamStarted(String streamId, String eventId);
    
    void publishStreamEnded(String streamId, String eventId);
    
    void publishReaction(String streamId, Reaction reaction);
    
    void publishChatMessage(String streamId, ChatMessage message);
    
    void publishViewerCount(String streamId, int viewerCount);
}
