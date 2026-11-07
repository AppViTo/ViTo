package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.RSVP;
import com.vito.event.domain.port.outbound.RSVPRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
    public List<RSVP> findByEventId(String eventId, RSVP.RSVPStatus status, int page, int size) {
        Criteria criteria = Criteria.where("eventId").is(eventId);
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Order.asc("respondedAt"), Sort.Order.asc("_id")))
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
    
    @Override
    public Optional<RSVP> promoteOldestWaitlisted(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("status").is(RSVP.RSVPStatus.WAITLISTED))
                .with(Sort.by(Sort.Direction.ASC, "waitlistedAt"));
        Update update = new Update()
                .set("status", RSVP.RSVPStatus.ACCEPTED)
                .unset("waitlistedAt");
        // Sin returnNew: el estado anterior sirve para revertir si no queda lugar
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options(), RSVP.class, "rsvps"));
    }
    
    @Override
    public void revertToWaitlisted(String rsvpId, Instant waitlistedAt) {
        Query query = new Query(Criteria.where("id").is(rsvpId).and("status").is(RSVP.RSVPStatus.ACCEPTED));
        Update update = new Update().set("status", RSVP.RSVPStatus.WAITLISTED).set("waitlistedAt", waitlistedAt);
        mongoTemplate.updateFirst(query, update, RSVP.class, "rsvps");
    }
    
    /**
     * T021 (Principio VII): un RSVP por persona y evento, y el orden de la lista de espera.
     * Se crean después de {@code Ola1DataMigration}, que quita los duplicados.
     */
    @Override
    public List<String> findEventIdsAnsweredBy(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId).and("status").ne(RSVP.RSVPStatus.DECLINED));
        return mongoTemplate.findDistinct(query, "eventId", "rsvps", String.class);
    }

    /** {@code {userId, status}}: eventos de una persona para {@code scope=invited} (R10). */
    public void ensureIndexes() {
        var indexOps = mongoTemplate.indexOps("rsvps");
        indexOps.createIndex(new Index().on("userId", Sort.Direction.ASC).on("status", Sort.Direction.ASC)
                .named("userId_status"));
        indexOps.createIndex(new Index().on("eventId", Sort.Direction.ASC).on("userId", Sort.Direction.ASC)
                .unique().named("eventId_userId_unique"));
        indexOps.createIndex(new Index().on("eventId", Sort.Direction.ASC).on("status", Sort.Direction.ASC)
                .on("waitlistedAt", Sort.Direction.ASC).named("eventId_status_waitlistedAt"));
    }
}
