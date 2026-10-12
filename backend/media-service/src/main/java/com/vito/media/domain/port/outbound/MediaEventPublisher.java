package com.vito.media.domain.port.outbound;

import com.vito.media.domain.model.Media;

import java.util.List;
import java.util.Optional;

public interface MediaEventPublisher {

    void publishMediaUploaded(Media media);
    void publishMediaProcessed(Media media);
    void publishMediaDeleted(String mediaId, String eventId);
}
