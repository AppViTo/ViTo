package com.vito.streaming.infrastructure.adapter.outbound.persistence;

import com.vito.streaming.domain.model.Reaction;
import com.vito.streaming.domain.port.outbound.ReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ReactionMongoAdapter implements ReactionRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public Reaction save(Reaction reaction) {
        return mongoTemplate.save(reaction, "reactions");
    }
    
    @Override
    public List<Reaction> findByStreamId(String streamId, int page, int size) {
        Query query = new Query(Criteria.where("streamId").is(streamId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Reaction.class, "reactions");
    }
    
    @Override
    public long countByStreamId(String streamId) {
        Query query = new Query(Criteria.where("streamId").is(streamId));
        return mongoTemplate.count(query, Reaction.class, "reactions");
    }
    
    @Override
    public long countByStreamIdAndType(String streamId, Reaction.ReactionType type) {
        Query query = new Query(Criteria.where("streamId").is(streamId)
                .and("type").is(type));
        return mongoTemplate.count(query, Reaction.class, "reactions");
    }
    
    @Override
    public void delete(String reactionId) {
        Query query = new Query(Criteria.where("id").is(reactionId));
        mongoTemplate.remove(query, Reaction.class, "reactions");
    }
}
