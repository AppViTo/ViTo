package com.vito.media.domain.port.inbound;

import com.vito.media.domain.model.Media;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface MediaService {

    Media uploadMedia(MultipartFile file, String eventId, String userId);
    Optional<Media> getMediaById(String mediaId);
    List<Media> getMediaByEventId(String eventId, Media.MediaType type);
    List<Media> getMediaByUserId(String userId);
    Media updateMedia(String mediaId, String fileName, String userId);
    void deleteMedia(String mediaId, String userId);
    String generatePresignedUrl(String mediaId, long expirationMinutes);
    Media processMedia(String mediaId);
}
