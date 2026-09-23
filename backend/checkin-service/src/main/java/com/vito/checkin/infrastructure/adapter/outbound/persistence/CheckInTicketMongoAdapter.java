package com.vito.checkin.infrastructure.adapter.outbound.persistence;

import com.vito.checkin.domain.model.CheckInTicket;
import com.vito.checkin.domain.port.outbound.CheckInTicketRepository;
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
public class CheckInTicketMongoAdapter implements CheckInTicketRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public CheckInTicket save(CheckInTicket ticket) {
        return mongoTemplate.save(ticket, "checkin_tickets");
    }
    
    @Override
    public Optional<CheckInTicket> findById(String ticketId) {
        Query query = new Query(Criteria.where("id").is(ticketId));
        CheckInTicket ticket = mongoTemplate.findOne(query, CheckInTicket.class, "checkin_tickets");
        return Optional.ofNullable(ticket);
    }
    
    @Override
    public Optional<CheckInTicket> findByQrCode(String qrCode) {
        Query query = new Query(Criteria.where("qrCode").is(qrCode));
        CheckInTicket ticket = mongoTemplate.findOne(query, CheckInTicket.class, "checkin_tickets");
        return Optional.ofNullable(ticket);
    }
    
    @Override
    public Optional<CheckInTicket> findByEventIdAndUserId(String eventId, String userId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("userId").is(userId));
        CheckInTicket ticket = mongoTemplate.findOne(query, CheckInTicket.class, "checkin_tickets");
        return Optional.ofNullable(ticket);
    }
    
    @Override
    public List<CheckInTicket> findByEventId(String eventId, int page, int size) {
        Query query = new Query(Criteria.where("eventId").is(eventId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, CheckInTicket.class, "checkin_tickets");
    }
    
    @Override
    public List<CheckInTicket> findByEventIdAndStatus(String eventId, CheckInTicket.CheckInStatus status) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("status").is(status));
        return mongoTemplate.find(query, CheckInTicket.class, "checkin_tickets");
    }
    
    @Override
    public long countByEventIdAndStatus(String eventId, CheckInTicket.CheckInStatus status) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("status").is(status));
        return mongoTemplate.count(query, CheckInTicket.class, "checkin_tickets");
    }
    
    @Override
    public void delete(String ticketId) {
        Query query = new Query(Criteria.where("id").is(ticketId));
        mongoTemplate.remove(query, CheckInTicket.class, "checkin_tickets");
    }
    
    @Override
    public boolean existsByEventIdAndUserId(String eventId, String userId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("userId").is(userId));
        return mongoTemplate.exists(query, CheckInTicket.class, "checkin_tickets");
    }
}
