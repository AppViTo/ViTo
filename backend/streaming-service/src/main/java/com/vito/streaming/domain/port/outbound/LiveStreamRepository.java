package com.vito.streaming.domain.port.outbound;

import com.vito.streaming.domain.model.LiveStream;

import java.util.List;
import java.util.Optional;

public interface LiveStreamRepository {
    
    LiveStream save(LiveStream stream);
    
    Optional<LiveStream> findById(String streamId);
    
    Optional<LiveStream> findByEventId(String eventId);
    
    List<LiveStream> findByOrganizerId(String organizerId, int page, int size);
    
    List<LiveStream> findLiveStreams(int page, int size);
    
    void delete(String streamId);
    
    boolean existsById(String streamId);
}
