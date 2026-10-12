package com.vito.mosaic.infrastructure.adapter.outbound.messaging;

import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicItem;
import com.vito.mosaic.domain.port.outbound.MosaicEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMosaicEventPublisher implements MosaicEventPublisher {

    static final String TOPIC = "mosaic-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishNewItem(String eventId, MosaicFeedItem item) {
        send(eventId, "MOSAIC_ITEM_CREATED", Map.of("item", item));
    }

    @Override
    public void publishGridUpdate(String eventId, MosaicItem item, int position) {
        send(eventId, "MOSAIC_GRID_UPDATED", Map.of("item", item, "position", position));
    }

    @Override
    public void publishLikeUpdate(String eventId, String itemId, int likesCount) {
        send(eventId, "MOSAIC_LIKES_UPDATED", Map.of("itemId", itemId, "likesCount", likesCount));
    }

    /** Clave = eventId: el orden de las actualizaciones del mosaico de un evento se conserva. */
    private void send(String eventId, String type, Map<String, Object> fields) {
        Map<String, Object> payload = new HashMap<>(fields);
        payload.put("type", type);
        payload.put("eventId", eventId);
        payload.put("timestamp", System.currentTimeMillis());

        kafkaTemplate.send(TOPIC, eventId, payload);
        log.debug("Published {} for event: {}", type, eventId);
    }
}
