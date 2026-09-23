package com.vito.social.infrastructure.adapter.outbound.messaging;

import com.vito.social.domain.port.outbound.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisherPort {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Override
    public void publishFollowEvent(String followerId, String followingId) {
        Map<String, Object> event = Map.of(
                "type", "USER_FOLLOWED",
                "followerId", followerId,
                "followingId", followingId,
                "timestamp", System.currentTimeMillis()
        );
        
        kafkaTemplate.send("social-events", followerId, event);
        log.info("Published follow event: {} -> {}", followerId, followingId);
    }
    
    @Override
    public void publishUnfollowEvent(String followerId, String followingId) {
        Map<String, Object> event = Map.of(
                "type", "USER_UNFOLLOWED",
                "followerId", followerId,
                "followingId", followingId,
                "timestamp", System.currentTimeMillis()
        );
        
        kafkaTemplate.send("social-events", followerId, event);
        log.info("Published unfollow event: {} -> {}", followerId, followingId);
    }
}
