package com.vito.streaming.infrastructure.adapter.outbound.persistence;

import com.vito.streaming.domain.model.LiveStream;
import com.vito.streaming.domain.port.outbound.LiveStreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LiveStreamMongoAdapter implements LiveStreamRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public LiveStream save(LiveStream stream) {
        return mongoTemplate.save(stream, "live_streams");
    }
    
    @Override
    public Optional<LiveStream> findById(String streamId) {
        Query query = new Query(Criteria.where("id").is(streamId));
        LiveStream stream = mongoTemplate.findOne(query, LiveStream.class, "live_streams");
        return Optional.ofNullable(stream);
    }
    
    @Override
    public Optional<LiveStream> findByEventId(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        LiveStream stream = mongoTemplate.findOne(query, LiveStream.class, "live_streams");
        return Optional.ofNullable(stream);
    }
    
    @Override
    public List<LiveStream> findByOrganizerId(String organizerId, int page, int size) {
        Query query = new Query(Criteria.where("organizerId").is(organizerId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, LiveStream.class, "live_streams");
    }
    
    @Override
    public List<LiveStream> findLiveStreams(int page, int size) {
        Query query = new Query(Criteria.where("status").is(LiveStream.StreamStatus.LIVE))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, LiveStream.class, "live_streams");
    }
    
    @Override
    public void delete(String streamId) {
        Query query = new Query(Criteria.where("id").is(streamId));
        mongoTemplate.remove(query, LiveStream.class, "live_streams");
    }
    
    @Override
    public boolean existsById(String streamId) {
        Query query = new Query(Criteria.where("id").is(streamId));
        return mongoTemplate.exists(query, LiveStream.class, "live_streams");
    }
}
