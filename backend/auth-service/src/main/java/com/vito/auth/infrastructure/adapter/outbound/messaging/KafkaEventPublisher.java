package com.vito.auth.infrastructure.adapter.outbound.messaging;

import com.vito.auth.domain.port.outbound.EventPublisherPort;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisherPort {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public void publish(String topic, String key, Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, jsonPayload);
            log.info("Event published to topic: {} with key: {}", topic, key);
        } catch (Exception e) {
            log.error("Failed to publish event to topic: {} with key: {}", topic, key, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
