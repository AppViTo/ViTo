package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.Event;
import com.vito.event.domain.port.outbound.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EventMongoAdapter implements EventRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public Event save(Event event) {
        return mongoTemplate.save(event, "events");
    }
    
    @Override
    public Optional<Event> findById(String eventId) {
        Query query = new Query(Criteria.where("id").is(eventId));
        Event event = mongoTemplate.findOne(query, Event.class, "events");
        return Optional.ofNullable(event);
    }
    
    @Override
    public List<Event> findByOrganizerId(String organizerId, int page, int size) {
        Query query = new Query(Criteria.where("organizerId").is(organizerId))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Event.class, "events");
    }
    
    @Override
    public List<Event> findByStatus(Event.EventStatus status, int page, int size) {
        Query query = new Query(Criteria.where("status").is(status))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Event.class, "events");
    }
    
    @Override
    public List<Event> findUpcomingEvents(int page, int size) {
        Query query = new Query(Criteria.where("status").is(Event.EventStatus.PUBLISHED)
                .and("details.startDate").gte(LocalDateTime.now()))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "details.startDate"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Event.class, "events");
    }
    
    @Override
    public List<Event> findByTag(String tag, int page, int size) {
        Query query = new Query(Criteria.where("details.tags").in(tag)
                .and("status").is(Event.EventStatus.PUBLISHED))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Event.class, "events");
    }
    
    @Override
    public void delete(String eventId) {
        Query query = new Query(Criteria.where("id").is(eventId));
        mongoTemplate.remove(query, Event.class, "events");
    }
    
    @Override
    public boolean existsById(String eventId) {
        Query query = new Query(Criteria.where("id").is(eventId));
        return mongoTemplate.exists(query, Event.class, "events");
    }
}
