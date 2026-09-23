package com.vito.chat.infrastructure.adapter.outbound;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.port.outbound.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatMessageMongoAdapter implements ChatMessageRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public ChatMessage save(ChatMessage message) {
        return mongoTemplate.save(message);
    }

    @Override
    public Optional<ChatMessage> findById(String id) {
        ChatMessage message = mongoTemplate.findById(id, ChatMessage.class);
        return Optional.ofNullable(message);
    }

    @Override
    public List<ChatMessage> findByRoomId(String roomId, int page, int size) {
        Query query = new Query(Criteria.where("roomId").is(roomId))
                .with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));
        return mongoTemplate.find(query, ChatMessage.class);
    }

    @Override
    public long countByRoomId(String roomId) {
        Query query = new Query(Criteria.where("roomId").is(roomId));
        return mongoTemplate.count(query, ChatMessage.class);
    }

    @Override
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, ChatMessage.class);
    }

    @Override
    public void markAsRead(String roomId, String userId) {
        Query query = new Query(Criteria.where("roomId").is(roomId)
                .and("senderId").ne(userId));
        Update update = new Update().set("read", true);
        mongoTemplate.updateMulti(query, update, ChatMessage.class);
    }
}
