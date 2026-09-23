package com.vito.streaming.domain.port.outbound;

import com.vito.streaming.domain.model.LiveStream;

import java.util.Optional;

public interface MediaServerPort {
    
    LiveStream createStream(String streamId, LiveStream.StreamSettings settings);
    
    LiveStream startStream(String streamKey);
    
    LiveStream stopStream(String streamKey);
    
    Optional<String> getStreamStatus(String streamKey);
    
    String generateStreamKey(String eventId, String userId);
    
    String getPlaybackUrl(String streamKey);
    
    boolean validateStreamKey(String streamKey);
}
