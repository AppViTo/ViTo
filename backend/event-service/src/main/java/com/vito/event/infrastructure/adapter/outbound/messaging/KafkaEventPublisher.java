package com.vito.event.infrastructure.adapter.outbound.messaging;

import com.vito.event.domain.port.outbound.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisherPort {

    static final String TOPIC = "event-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishEventCreated(String eventId, String organizerId) {
        send(eventId, "EVENT_CREATED", Map.of("organizerId", organizerId));
    }

    @Override
    public void publishEventUpdated(String eventId, String organizerId) {
        send(eventId, "EVENT_UPDATED", Map.of("organizerId", organizerId));
    }

    @Override
    public void publishEventCancelled(String eventId, String organizerId) {
        send(eventId, "EVENT_CANCELLED", Map.of("organizerId", organizerId));
    }

    @Override
    public void publishRSVPChanged(String eventId, String userId, String status) {
        send(eventId, "RSVP_CHANGED", Map.of("userId", userId, "status", status));
    }

    @Override
    public void publishInvitationSent(String eventId, String inviterId, String inviteeId) {
        send(eventId, "INVITATION_SENT", Map.of("inviterId", inviterId, "inviteeId", inviteeId));
    }

    @Override
    public void publishCheckIn(String eventId, String userId) {
        send(eventId, "USER_CHECKED_IN", Map.of("userId", userId));
    }

    /** Clave = eventId, para que todos los mensajes de un evento queden ordenados en la misma partición. */
    private void send(String eventId, String type, Map<String, Object> fields) {
        Map<String, Object> payload = new HashMap<>(fields);
        payload.put("type", type);
        payload.put("eventId", eventId);
        payload.put("timestamp", System.currentTimeMillis());

        kafkaTemplate.send(TOPIC, eventId, payload);
        log.info("Published {} for event: {}", type, eventId);
    }
}
