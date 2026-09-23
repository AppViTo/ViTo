package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.RSVPRepository;
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
public class RSVPMongoAdapter implements RSVPRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public RSVP save(RSVP rsvp) {
        return mongoTemplate.save(rsvp, "rsvps");
    }
    
    @Override
    public Optional<RSVP> findByEventIdAndUserId(String eventId, String userId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("userId").is(userId));
        RSVP rsvp = mongoTemplate.findOne(query, RSVP.class, "rsvps");
        return Optional.ofNullable(rsvp);
    }
    
    @Override
    public List<RSVP> findByEventId(String eventId, int page, int size) {
        Query query = new Query(Criteria.where("eventId").is(eventId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, RSVP.class, "rsvps");
    }
    
    @Override
    public List<RSVP> findByUserId(String userId, int page, int size) {
        Query query = new Query(Criteria.where("userId").is(userId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, RSVP.class, "rsvps");
    }
    
    @Override
    public List<RSVP> findByEventIdAndStatus(String eventId, RSVP.RSVPStatus status) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("status").is(status));
        return mongoTemplate.find(query, RSVP.class, "rsvps");
    }
    
    @Override
    public long countByEventId(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        return mongoTemplate.count(query, RSVP.class, "rsvps");
    }
    
    @Override
    public long countByEventIdAndStatus(String eventId, RSVP.RSVPStatus status) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("status").is(status));
        return mongoTemplate.count(query, RSVP.class, "rsvps");
    }
    
    @Override
    public void delete(String eventId, String userId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("userId").is(userId));
        mongoTemplate.remove(query, RSVP.class, "rsvps");
    }
    
    @Override
    public boolean existsByEventIdAndUserId(String eventId, String userId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("userId").is(userId));
        return mongoTemplate.exists(query, RSVP.class, "rsvps");
    }
}
