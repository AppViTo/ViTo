package com.vito.chat.infrastructure.adapter.outbound;

import com.vito.chat.domain.model.ChatRoom;
import com.vito.chat.domain.port.outbound.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatRoomMongoAdapter implements ChatRoomRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public ChatRoom save(ChatRoom room) {
        return mongoTemplate.save(room);
    }

    @Override
    public Optional<ChatRoom> findById(String id) {
        ChatRoom room = mongoTemplate.findById(id, ChatRoom.class);
        return Optional.ofNullable(room);
    }

    @Override
    public List<ChatRoom> findByEventId(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        return mongoTemplate.find(query, ChatRoom.class);
    }

    @Override
    public List<ChatRoom> findByParticipantId(String userId) {
        Query query = new Query(Criteria.where("participantIds").in(userId));
        return mongoTemplate.find(query, ChatRoom.class);
    }

    @Override
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, ChatRoom.class);
    }
}
