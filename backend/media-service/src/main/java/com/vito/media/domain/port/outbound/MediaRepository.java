package com.vito.media.domain.port.outbound;

import com.vito.media.domain.model.Media;

import java.util.List;
import java.util.Optional;

public interface MediaRepository {

    Media save(Media media);
    Optional<Media> findById(String id);
    /** Sin borrados, por {@code uploadedAt} descendente y paginado en la base (CONVENTIONS §5). */
    List<Media> findByEventId(String eventId, int page, int size);
    /** Sin borrados; los elementos del mosaico apuntan a su foto por {@code location.url}. */
    List<Media> findByEventIdAndUrls(String eventId, List<String> urls);
    List<Media> findByUploadedBy(String userId);
    void deleteById(String id);
}
