package com.vito.streaming.domain.port.inbound;

import com.vito.streaming.domain.model.ChatMessage;
import com.vito.streaming.domain.model.LiveStream;
import com.vito.streaming.domain.model.Reaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public interface StreamingService {
    
    LiveStream createStream(CreateStreamCommand command);
    
    LiveStream startStream(String streamId, String userId);
    
    LiveStream stopStream(String streamId, String userId);
    
    LiveStream getStream(String streamId);
    
    LiveStream getStreamByEventId(String eventId);
    
    List<LiveStream> getLiveStreams(int page, int size);
    
    Reaction addReaction(AddReactionCommand command);
    
    List<Reaction> getReactions(String streamId, int page, int size);
    
    ChatMessage sendChatMessage(SendChatMessageCommand command);
    
    List<ChatMessage> getChatMessages(String streamId, int page, int size);
    
    StreamInfo getStreamInfo(String streamId);
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CreateStreamCommand {
        private String eventId;
        private String organizerId;
        private String title;
        private String description;
        private LiveStream.StreamSettings settings;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AddReactionCommand {
        private String streamId;
        private String userId;
        private String username;
        private String displayName;
        private String avatar;
        private Reaction.ReactionType type;
        private String emoji;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SendChatMessageCommand {
        private String streamId;
        private String userId;
        private String username;
        private String displayName;
        private String avatar;
        private String message;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class StreamInfo {
        private LiveStream stream;
        private int currentViewers;
        private int totalReactions;
        private int totalMessages;
        private long duration;
    }
}
