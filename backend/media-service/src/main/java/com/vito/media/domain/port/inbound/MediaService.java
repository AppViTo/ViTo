package com.vito.media.domain.port.inbound;

import com.vito.media.domain.model.Media;
import com.vito.media.domain.model.PresignedUrl;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface MediaService {

    Media uploadMedia(MultipartFile file, String eventId, String userId);
    Optional<Media> getMediaById(String mediaId);
    /** {@code listEventMedia} (T-C09): organizador o RSVP {@code ACCEPTED}; {@code page} >= 0, {@code size} 1-100. */
    List<Media> listEventMedia(String eventId, String userId, int page, int size);
    List<Media> getMediaByUserId(String userId);
    Media updateMedia(String mediaId, String fileName, String userId);
    /** {@code deleteMedia} (T-C09): quien lo subió o el organizador; borra archivo, miniatura y registro. */
    void deleteMedia(String mediaId, String userId);
    /** {@code getMediaPresignedUrl} (T-C09): mismo acceso que la lista; {@code expiresIn} 1-3600, por defecto 900. */
    PresignedUrl generatePresignedUrl(String mediaId, String userId, Integer expiresIn);
    /** Decisión 2 de T-C09: retirar un elemento del mosaico borra sus archivos. Idempotente. */
    int deleteMosaicItemMedia(String eventId, List<String> mediaUrls);
    Media processMedia(String mediaId);
}
