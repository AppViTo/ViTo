package com.vito.media.infrastructure.adapter.inbound.messaging;

import com.vito.media.domain.port.inbound.MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * T-B33, decisión 2 de T-C09: cuando el organizador retira un elemento del mosaico, mosaic-service
 * publica {@code MOSAIC_ITEM_REMOVED} con sus {@code mediaUrls} y aquí se borran esos archivos
 * (y su registro) del almacenamiento. El borrado es idempotente: un mensaje repetido no encuentra
 * nada que borrar. Los demás tipos de {@code mosaic-events} se ignoran.
 */
@Slf4j
@Component
public class MosaicEventsListener {

    static final String TOPIC = "mosaic-events";
    static final String ITEM_REMOVED = "MOSAIC_ITEM_REMOVED";

    private final MediaService mediaService;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public MosaicEventsListener(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @KafkaListener(topics = TOPIC, autoStartup = "${media.mosaic-events.listener-enabled:true}")
    public void onMosaicEvent(String payload) {
        JsonNode event;
        try {
            event = jsonMapper.readTree(payload);
        } catch (JacksonException e) {
            log.warn("Ignoring unreadable {} message: {}", TOPIC, e.getOriginalMessage());
            return;
        }
        if (!ITEM_REMOVED.equals(event.path("type").asString(null))) {
            return;
        }
        String eventId = event.path("eventId").asString(null);
        List<String> urls = new ArrayList<>();
        event.path("mediaUrls").forEach(url -> {
            if (url.isString()) {
                urls.add(url.asString());
            }
        });
        mediaService.deleteMosaicItemMedia(eventId, urls);
    }
}
