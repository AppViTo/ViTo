package com.vito.streaming.infrastructure.adapter.outbound.messaging;

import com.vito.streaming.domain.model.ChatMessage;
import com.vito.streaming.domain.model.Reaction;
import com.vito.streaming.domain.port.outbound.StreamingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaStreamingEventPublisher implements StreamingEventPublisher {

    static final String TOPIC = "streaming-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishStreamStarted(String streamId, String eventId) {
        send(streamId, "STREAM_STARTED", Map.of("eventId", eventId));
    }

    @Override
    public void publishStreamEnded(String streamId, String eventId) {
        send(streamId, "STREAM_ENDED", Map.of("eventId", eventId));
    }

    @Override
    public void publishReaction(String streamId, Reaction reaction) {
        send(streamId, "STREAM_REACTION", Map.of("reaction", reaction));
    }

    @Override
    public void publishChatMessage(String streamId, ChatMessage message) {
        send(streamId, "STREAM_CHAT_MESSAGE", Map.of("message", message));
    }

    @Override
    public void publishViewerCount(String streamId, int viewerCount) {
        send(streamId, "STREAM_VIEWER_COUNT", Map.of("viewerCount", viewerCount));
    }

    /** Clave = streamId: los mensajes de una transmisión quedan ordenados en la misma partición. */
    private void send(String streamId, String type, Map<String, Object> fields) {
        Map<String, Object> payload = new HashMap<>(fields);
        payload.put("type", type);
        payload.put("streamId", streamId);
        payload.put("timestamp", System.currentTimeMillis());

        kafkaTemplate.send(TOPIC, streamId, payload);
        log.debug("Published {} for stream: {}", type, streamId);
    }
}
