package com.vito.archive.infrastructure.adapter.outbound.persistence;

import com.vito.archive.domain.model.EventArchive;
import com.vito.archive.domain.port.outbound.EventArchiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EventArchiveMongoAdapter implements EventArchiveRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public EventArchive save(EventArchive archive) {
        return mongoTemplate.save(archive, "event_archives");
    }
    
    @Override
    public Optional<EventArchive> findById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        EventArchive archive = mongoTemplate.findOne(query, EventArchive.class, "event_archives");
        return Optional.ofNullable(archive);
    }
    
    @Override
    public Optional<EventArchive> findByEventId(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        EventArchive archive = mongoTemplate.findOne(query, EventArchive.class, "event_archives");
        return Optional.ofNullable(archive);
    }
    
    @Override
    public List<EventArchive> findAll(int page, int size) {
        Query query = new Query()
                .with(PageRequest.of(page, size))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, EventArchive.class, "event_archives");
    }
    
    @Override
    public List<EventArchive> findByOrganizerId(String organizerId, int page, int size) {
        Query query = new Query(Criteria.where("organizerId").is(organizerId))
                .with(PageRequest.of(page, size))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, EventArchive.class, "event_archives");
    }
    
    @Override
    public List<EventArchive> findByIsPublicTrue(int page, int size) {
        Query query = new Query(Criteria.where("sharing.isPublic").is(true))
                .with(PageRequest.of(page, size))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, EventArchive.class, "event_archives");
    }
    
    @Override
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, EventArchive.class, "event_archives");
    }
}
