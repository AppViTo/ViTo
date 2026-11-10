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

    static final String TOPIC = "social-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishFollowEvent(String followerId, String followingId) {
        publish("USER_FOLLOWED", followerId, followingId);
    }

    @Override
    public void publishUnfollowEvent(String followerId, String followingId) {
        publish("USER_UNFOLLOWED", followerId, followingId);
    }

    /**
     * T020 (Ola 2a, R10): {@code send} es asíncrono y antes nadie miraba su resultado, así que un
     * envío fallido no dejaba rastro. Se registra el resultado con el tipo de evento, nunca con
     * ids de personas. Sigue siendo "best effort": el outbox llega con T-C05 (Principio V).
     */
    private void publish(String type, String followerId, String followingId) {
        Map<String, Object> event = Map.of(
                "type", type,
                "followerId", followerId,
                "followingId", followingId,
                "timestamp", System.currentTimeMillis()
        );
        kafkaTemplate.send(TOPIC, followerId, event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("No se publicó {} en {}: {}", type, TOPIC, error.toString());
            } else {
                log.debug("Publicado {} en {}", type, TOPIC);
            }
        });
    }
}
