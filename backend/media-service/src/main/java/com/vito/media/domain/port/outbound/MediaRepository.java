package com.vito.media.domain.port.outbound;

import com.vito.media.domain.model.Media;

import java.util.List;
import java.util.Optional;

public interface MediaRepository {

    Media save(Media media);
    Optional<Media> findById(String id);
    List<Media> findByEventId(String eventId);
    List<Media> findByEventIdAndType(String eventId, Media.MediaType type);
    List<Media> findByUploadedBy(String userId);
    void deleteById(String id);
}
