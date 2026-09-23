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

public interface StoragePort {

    String uploadFile(String key, byte[] data, String contentType);
    byte[] downloadFile(String key);
    void deleteFile(String key);
    String generatePresignedUrl(String key, long expirationMinutes);
    String getPublicUrl(String key);
}

public interface MediaEventPublisher {

    void publishMediaUploaded(Media media);
    void publishMediaProcessed(Media media);
    void publishMediaDeleted(String mediaId, String eventId);
}

public interface ImageProcessingPort {

    Media.MediaMetadata extractMetadata(byte[] data, String contentType);
    byte[] createThumbnail(byte[] data, int width, int height);
    byte[] resizeImage(byte[] data, int maxWidth, int maxHeight);
    boolean isImage(String contentType);
    boolean isVideo(String contentType);
}
