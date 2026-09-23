package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.domain.model.Media;
import com.vito.media.domain.port.outbound.MediaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMediaEventPublisher implements MediaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_MEDIA = "media-events";

    @Override
    public void publishMediaUploaded(Media media) {
        try {
            var event = new MediaEvent(media.getId(), media.getEventId(), media.getUploadedBy(), "UPLOADED");
            kafkaTemplate.send(TOPIC_MEDIA, media.getEventId(), event);
            log.debug("Published media uploaded event: mediaId={}", media.getId());
        } catch (Exception e) {
            log.error("Failed to publish media uploaded event: {}", e.getMessage(), e);
        }
    }

    @Override
    public void publishMediaProcessed(Media media) {
        try {
            var event = new MediaEvent(media.getId(), media.getEventId(), media.getUploadedBy(), "PROCESSED");
            kafkaTemplate.send(TOPIC_MEDIA, media.getEventId(), event);
            log.debug("Published media processed event: mediaId={}", media.getId());
        } catch (Exception e) {
            log.error("Failed to publish media processed event: {}", e.getMessage(), e);
        }
    }

    @Override
    public void publishMediaDeleted(String mediaId, String eventId) {
        try {
            var event = new MediaEvent(mediaId, eventId, null, "DELETED");
            kafkaTemplate.send(TOPIC_MEDIA, eventId, event);
            log.debug("Published media deleted event: mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("Failed to publish media deleted event: {}", e.getMessage(), e);
        }
    }

    public record MediaEvent(String mediaId, String eventId, String userId, String action) {}
}
