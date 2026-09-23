package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.Invitation;
import com.vito.event.domain.port.outbound.InvitationRepository;
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
}
