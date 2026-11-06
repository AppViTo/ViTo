package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.Event;
import com.vito.event.domain.model.EventPatch;
import com.vito.event.domain.port.outbound.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
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
        // Solo eventos públicos: los privados se ven por invitación (T-B17, ADR-014)
        Query query = new Query(Criteria.where("status").is(Event.EventStatus.PUBLISHED)
                .and("settings.isPublic").is(true)
                .and("details.startDate").gte(Instant.now()))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "details.startDate"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Event.class, "events");
    }
    
    @Override
    public List<Event> findActiveByIds(Collection<String> ids, Instant now, int page, int size) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Query query = new Query(Criteria.where("_id").in(ids.stream().map(EventMongoAdapter::idValue).toList())
                .and("status").is(Event.EventStatus.PUBLISHED)
                .and("details.endDate").gt(now))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "details.startDate"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Event.class, "events");
    }

    /** Los ids de eventos se guardan como ObjectId; en invitaciones y RSVP, como texto. */
    private static Object idValue(String id) {
        return org.bson.types.ObjectId.isValid(id) ? new org.bson.types.ObjectId(id) : id;
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
    
    @Override
    public Optional<Event> applyPatch(String eventId, EventPatch patch, String organizerDisplayName, Instant now) {
        Update update = new Update();
        set(update, "title", patch.title());
        set(update, "description", patch.description());
        set(update, "details.startDate", patch.startDate());
        set(update, "details.endDate", patch.endDate());
        set(update, "details.location", patch.location());
        set(update, "details.address", patch.address());
        set(update, "details.latitude", patch.latitude());
        set(update, "details.longitude", patch.longitude());
        set(update, "details.coverImages", patch.coverImages());
        set(update, "details.tags", patch.tags());
        set(update, "details.category", patch.category());
        set(update, "settings.isPublic", patch.isPublic());
        set(update, "settings.requiresApproval", patch.requiresApproval());
        set(update, "settings.maxAttendees", patch.maxAttendees());
        set(update, "settings.allowGuestInvites", patch.allowGuestInvites());
        set(update, "settings.enableLiveStreaming", patch.enableLiveStreaming());
        set(update, "settings.enableMosaic", patch.enableMosaic());
        set(update, "settings.hideAddressUntilAccepted", patch.hideAddressUntilAccepted());
        if (organizerDisplayName != null) {
            update.set("organizerDisplayName", organizerDisplayName);
        }
        update.set("updatedAt", now);

        // Editable: ni cancelado ni completado, y sin terminar (Event.isEditableAt)
        List<Criteria> conditions = new ArrayList<>(List.of(
                Criteria.where("id").is(eventId),
                Criteria.where("status").nin(Event.EventStatus.CANCELLED, Event.EventStatus.COMPLETED),
                new Criteria().orOperator(
                        Criteria.where("details.endDate").is(null),
                        Criteria.where("details.endDate").gt(now))));
        Integer maxAttendees = patch.maxAttendees() != null ? patch.maxAttendees().value() : null;
        if (maxAttendees != null && maxAttendees > 0) {
            // FR-021 en la misma operación: un RSVP concurrente no deja confirmados por encima del cupo
            conditions.add(new Criteria().orOperator(
                    Criteria.where("stats.totalAccepted").exists(false),
                    Criteria.where("stats.totalAccepted").lte(maxAttendees)));
        }
        Query query = new Query(new Criteria().andOperator(conditions));
        return Optional.ofNullable(mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Event.class, "events"));
    }

    @Override
    public boolean cancel(String eventId, Instant now) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("id").is(eventId),
                Criteria.where("status").nin(Event.EventStatus.CANCELLED, Event.EventStatus.COMPLETED),
                new Criteria().orOperator(
                        Criteria.where("details.endDate").is(null),
                        Criteria.where("details.endDate").gt(now))));
        Update update = new Update().set("status", Event.EventStatus.CANCELLED).set("updatedAt", now);
        return mongoTemplate.updateFirst(query, update, Event.class, "events").getModifiedCount() == 1;
    }

    /** Merge patch: valor nuevo con {@code $set}; {@code null} enviado con {@code $unset}. */
    private static void set(Update update, String path, EventPatch.Change<?> change) {
        if (change == null) {
            return;
        }
        if (change.value() == null) {
            update.unset(path);
        } else {
            update.set(path, change.value());
        }
    }

    @Override
    public boolean tryReserveSeat(String eventId) {
        // R1: comparar e incrementar en una sola operación sobre el documento del evento
        Query query = new Query(Criteria.where("id").is(eventId)
                .and("status").is(Event.EventStatus.PUBLISHED)
                .orOperator(
                        Criteria.where("settings.maxAttendees").is(0),
                        Criteria.expr(ComparisonOperators.Lt.valueOf("stats.totalAccepted")
                                .lessThan("settings.maxAttendees"))));
        Update update = new Update().inc("stats.totalAccepted", 1);
        return mongoTemplate.updateFirst(query, update, Event.class, "events").getModifiedCount() == 1;
    }
    
    @Override
    public void releaseSeat(String eventId) {
        Query query = new Query(Criteria.where("id").is(eventId).and("stats.totalAccepted").gt(0));
        mongoTemplate.updateFirst(query, new Update().inc("stats.totalAccepted", -1), Event.class, "events");
    }
    
    @Override
    public void incrementStat(String eventId, StatCounter counter, int delta) {
        if (delta == 0) {
            return;
        }
        Criteria criteria = Criteria.where("id").is(eventId);
        if (delta < 0) {
            criteria = criteria.and(counter.field()).gte(-delta);
        }
        mongoTemplate.updateFirst(new Query(criteria), new Update().inc(counter.field(), delta), Event.class, "events");
    }
}
