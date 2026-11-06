package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.port.outbound.InvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class InvitationMongoAdapter implements InvitationRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public Invitation save(Invitation invitation) {
        return mongoTemplate.save(invitation, "invitations");
    }
    
    @Override
    public Optional<Invitation> findByEventIdAndInviteeId(String eventId, String inviteeId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("inviteeId").is(inviteeId));
        Invitation invitation = mongoTemplate.findOne(query, Invitation.class, "invitations");
        return Optional.ofNullable(invitation);
    }
    
    @Override
    public List<Invitation> findByEventId(String eventId, int page, int size) {
        Query query = new Query(Criteria.where("eventId").is(eventId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Invitation.class, "invitations");
    }
    
    @Override
    public List<Invitation> findByInviterId(String inviterId, int page, int size) {
        Query query = new Query(Criteria.where("inviterId").is(inviterId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Invitation.class, "invitations");
    }
    
    @Override
    public List<Invitation> findByInviteeId(String inviteeId, int page, int size) {
        Query query = new Query(Criteria.where("inviteeId").is(inviteeId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Invitation.class, "invitations");
    }
    
    @Override
    public List<Invitation> findByEventIdAndStatus(String eventId, Invitation.InvitationStatus status) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("status").is(status));
        return mongoTemplate.find(query, Invitation.class, "invitations");
    }
    
    @Override
    public long countByEventId(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        return mongoTemplate.count(query, Invitation.class, "invitations");
    }
    
    @Override
    public void delete(String eventId, String inviteeId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("inviteeId").is(inviteeId));
        mongoTemplate.remove(query, Invitation.class, "invitations");
    }
    
    @Override
    public boolean existsByEventIdAndInviteeId(String eventId, String inviteeId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("inviteeId").is(inviteeId));
        return mongoTemplate.exists(query, Invitation.class, "invitations");
    }

    @Override
    public List<String> findEventIdsInviting(String inviteeId, Instant now) {
        Query query = new Query(Criteria.where("inviteeId").is(inviteeId).orOperator(
                Criteria.where("status").is(Invitation.InvitationStatus.ACCEPTED),
                new Criteria().andOperator(Criteria.where("status").is(Invitation.InvitationStatus.PENDING),
                        new Criteria().orOperator(Criteria.where("expiresAt").is(null), Criteria.where("expiresAt").gt(now)))));
        return mongoTemplate.findDistinct(query, "eventId", "invitations", String.class);
    }

    /** T021: una invitación por persona y evento; {@code {inviteeId, status}} para {@code scope=invited} (R10). */
    public void ensureIndexes() {
        var indexOps = mongoTemplate.indexOps("invitations");
        indexOps.createIndex(new Index().on("eventId", Sort.Direction.ASC).on("inviteeId", Sort.Direction.ASC)
                .unique().named("eventId_inviteeId_unique"));
        indexOps.createIndex(new Index().on("inviteeId", Sort.Direction.ASC).on("status", Sort.Direction.ASC)
                .named("inviteeId_status"));
    }
}
