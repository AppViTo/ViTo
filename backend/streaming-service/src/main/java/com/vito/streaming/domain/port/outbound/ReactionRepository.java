package com.vito.streaming.domain.port.outbound;

import com.vito.streaming.domain.model.Reaction;

import java.util.List;

public interface ReactionRepository {
    
    Reaction save(Reaction reaction);
    
    List<Reaction> findByStreamId(String streamId, int page, int size);
    
    long countByStreamId(String streamId);
    
    long countByStreamIdAndType(String streamId, Reaction.ReactionType type);
    
    void delete(String reactionId);
}
