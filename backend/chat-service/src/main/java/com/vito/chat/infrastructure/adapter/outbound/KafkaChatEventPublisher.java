package com.vito.chat.infrastructure.adapter.outbound;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.port.outbound.ChatEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaChatEventPublisher implements ChatEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_MESSAGES = "chat-messages";
    private static final String TOPIC_TYPING = "chat-typing";
    private static final String TOPIC_PRESENCE = "chat-presence";

    @Override
    public void publishMessage(ChatMessage message) {
        try {
            kafkaTemplate.send(TOPIC_MESSAGES, message.getRoomId(), message);
            log.debug("Published message to Kafka topic {}: {}", TOPIC_MESSAGES, message.getId());
        } catch (Exception e) {
            log.error("Failed to publish message to Kafka: {}", e.getMessage(), e);
        }
    }

    @Override
    public void publishTypingIndicator(String roomId, String userId, boolean isTyping) {
        try {
            var event = new TypingEvent(roomId, userId, isTyping);
            kafkaTemplate.send(TOPIC_TYPING, roomId, event);
            log.debug("Published typing indicator to Kafka: room={}, user={}, typing={}", 
                    roomId, userId, isTyping);
        } catch (Exception e) {
            log.error("Failed to publish typing indicator to Kafka: {}", e.getMessage(), e);
        }
    }

    @Override
    public void publishUserJoined(String roomId, String userId) {
        try {
            var event = new PresenceEvent(roomId, userId, "JOINED");
            kafkaTemplate.send(TOPIC_PRESENCE, roomId, event);
            log.debug("Published user joined event to Kafka: room={}, user={}", roomId, userId);
        } catch (Exception e) {
            log.error("Failed to publish user joined event to Kafka: {}", e.getMessage(), e);
        }
    }

    @Override
    public void publishUserLeft(String roomId, String userId) {
        try {
            var event = new PresenceEvent(roomId, userId, "LEFT");
            kafkaTemplate.send(TOPIC_PRESENCE, roomId, event);
            log.debug("Published user left event to Kafka: room={}, user={}", roomId, userId);
        } catch (Exception e) {
            log.error("Failed to publish user left event to Kafka: {}", e.getMessage(), e);
        }
    }

    public record TypingEvent(String roomId, String userId, boolean isTyping) {}
    public record PresenceEvent(String roomId, String userId, String action) {}
}
