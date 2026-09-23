package com.vito.streaming.infrastructure.adapter.outbound.persistence;

import com.vito.streaming.domain.model.ChatMessage;
import com.vito.streaming.domain.port.outbound.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatMongoAdapter implements ChatRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public ChatMessage save(ChatMessage message) {
        return mongoTemplate.save(message, "chat_messages");
    }
    
    @Override
    public List<ChatMessage> findByStreamId(String streamId, int page, int size) {
        Query query = new Query(Criteria.where("streamId").is(streamId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ChatMessage.class, "chat_messages");
    }
    
    @Override
    public long countByStreamId(String streamId) {
        Query query = new Query(Criteria.where("streamId").is(streamId));
        return mongoTemplate.count(query, ChatMessage.class, "chat_messages");
    }
    
    @Override
    public void delete(String messageId) {
        Query query = new Query(Criteria.where("id").is(messageId));
        mongoTemplate.remove(query, ChatMessage.class, "chat_messages");
    }
}
