package com.vito.media.application.service;

import com.vito.media.domain.exception.MediaException;
import com.vito.media.domain.model.EventAccess;
import com.vito.media.domain.model.Media;
import com.vito.media.domain.model.PresignedUrl;
import com.vito.media.domain.port.inbound.MediaService;
import com.vito.media.domain.port.outbound.EventAccessLookup;
import com.vito.media.domain.port.outbound.FileTypeDetectorPort;
import com.vito.media.domain.port.outbound.ImageProcessingPort;
import com.vito.media.domain.port.outbound.ImageReencoderPort;
import com.vito.media.domain.port.outbound.MediaEventPublisher;
import com.vito.media.domain.port.outbound.MediaRepository;
import com.vito.media.domain.port.outbound.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    private final StoragePort storagePort;
    private final MediaEventPublisher eventPublisher;
    private final ImageProcessingPort imageProcessingPort;
    private final FileTypeDetectorPort fileTypeDetector;
    private final ImageReencoderPort imageReencoder;
    private final EventAccessLookup eventAccessLookup;

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_URL_SECONDS = 900;
    static final int MAX_URL_SECONDS = 3600;
    // Organizador o RSVP ACCEPTED (decisión 3 de T-C09); mismo texto que los fixtures 403
    static final String NO_ACCESS = "No tienes acceso a los archivos de este evento";

    // CONVENTIONS §8: 10 MB y estos cuatro formatos
    @Value("${media.max-file-size:10485760}")
    private long maxFileSize;

    @Value("${media.allowed-types:image/jpeg,image/png,image/webp,video/mp4}")
    private String allowedTypes;

    @Value("${s3.bucket}")
    private String bucketName;

    @Override
    @Transactional
    public Media uploadMedia(MultipartFile file, String eventId, String userId) {
        validateFile(file);

        try {
            byte[] data = file.getBytes();
            String contentType = verifiedContentType(file.getContentType(), data);

            // T-B25 (excepción A3): solo se publica lo que se re-codifica; el vídeo no, en esta ola
            if (!imageReencoder.supports(contentType)) {
                throw new MediaException.VideoNotAllowedException();
            }
            ImageReencoderPort.ReencodedImage image = imageReencoder.reencode(data, contentType);
            data = image.data();
            contentType = image.contentType();
            Media.MediaMetadata metadata = Media.MediaMetadata.builder()
                    .width(image.width())
                    .height(image.height())
                    .format(contentType.substring("image/".length()))
                    .build();

            String fileName = generateFileName(contentType);
            String key = String.format("events/%s/%s/%s", eventId, userId, fileName);

            // Upload to S3
            String url = storagePort.uploadFile(key, data, contentType);

            // Create thumbnail for images
            Media.S3Location.S3LocationBuilder locationBuilder = Media.S3Location.builder()
                    .bucket(bucketName)
                    .key(key)
                    .url(url);

            if (imageProcessingPort.isImage(contentType) && metadata != null) {
                byte[] thumbnail = imageProcessingPort.createThumbnail(data, 200, 200);
                String thumbnailKey = key.replace(fileName, "thumb_" + fileName);
                String thumbnailUrl = storagePort.uploadFile(thumbnailKey, thumbnail, contentType);
                locationBuilder.thumbnailKey(thumbnailKey).thumbnailUrl(thumbnailUrl);
            }

            // Save to database
            Media media = Media.builder()
                    .eventId(eventId)
                    .uploadedBy(userId)
                    .fileName(fileName)
                    .originalFileName(file.getOriginalFilename())
                    .contentType(contentType)
                    .fileSize(data.length)
                    .status(Media.MediaStatus.UPLOADED)
                    .metadata(metadata)
                    .location(locationBuilder.build())
                    .uploadedAt(LocalDateTime.now())
                    .deleted(false)
                    .build();

            Media saved = mediaRepository.save(media);
            eventPublisher.publishMediaUploaded(saved);

            log.info("Media uploaded: {} for event {}", saved.getId(), eventId);
            return saved;

        } catch (IOException e) {
            throw new MediaException.UploadFailedException(e.getMessage());
        }
    }

    /** Un registro con el borrado lógico anterior a T-B33 responde como inexistente. */
    @Override
    @Transactional(readOnly = true)
    public Optional<Media> getMediaById(String mediaId) {
        return mediaRepository.findById(mediaId).filter(media -> !media.isDeleted());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Media> listEventMedia(String eventId, String userId, int page, int size) {
        if (page < 0) {
            throw new MediaException.InvalidParameterException("page", "page debe ser 0 o mayor",
                    "debe ser mayor o igual a 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new MediaException.InvalidParameterException("size", "size debe estar entre 1 y " + MAX_PAGE_SIZE,
                    size < 1 ? "debe ser mayor o igual a 1" : "debe ser menor o igual a " + MAX_PAGE_SIZE);
        }
        EventAccess access = eventAccessLookup.findAccess(eventId)
                .orElseThrow(MediaException.EventNotFoundException::new);
        if (!access.canView(userId)) {
            throw new MediaException.UnauthorizedException(
                    NO_ACCESS);
        }
        return mediaRepository.findByEventId(eventId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Media> getMediaByUserId(String userId) {
        return mediaRepository.findByUploadedBy(userId);
    }

    @Override
    @Transactional
    public Media updateMedia(String mediaId, String fileName, String userId) {
        Media media = activeMedia(mediaId);

        if (!media.getUploadedBy().equals(userId)) {
            throw new MediaException.UnauthorizedException("Can only update your own media");
        }

        media.setFileName(fileName);
        Media updated = mediaRepository.save(media);

        log.info("Media updated: {}", mediaId);
        return updated;
    }

    /**
     * Borra quien lo subió o el organizador (T-C09). Primero los archivos y después el registro:
     * si el almacenamiento falla, el registro sigue ahí y el borrado se puede repetir.
     */
    @Override
    @Transactional
    public void deleteMedia(String mediaId, String userId) {
        Media media = activeMedia(mediaId);

        if (!userId.equals(media.getUploadedBy())) {
            boolean organizer = eventAccessLookup.findAccess(media.getEventId())
                    .map(access -> access.isOrganizer(userId))
                    .orElse(false);
            if (!organizer) {
                throw new MediaException.UnauthorizedException(
                        "Solo quien subió el archivo o el organizador del evento puede borrarlo");
            }
        }

        delete(media);
        log.info("Media {} deleted", mediaId);
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedUrl generatePresignedUrl(String mediaId, String userId, Integer expiresIn) {
        int seconds = expiresIn == null ? DEFAULT_URL_SECONDS : expiresIn;
        if (seconds < 1 || seconds > MAX_URL_SECONDS) {
            throw new MediaException.InvalidParameterException("expiresIn",
                    "expiresIn debe estar entre 1 y " + MAX_URL_SECONDS + " segundos",
                    seconds < 1 ? "debe ser mayor o igual a 1" : "debe ser menor o igual a " + MAX_URL_SECONDS);
        }
        Media media = activeMedia(mediaId);
        boolean canView = eventAccessLookup.findAccess(media.getEventId())
                .map(access -> access.canView(userId))
                .orElse(false);
        if (!canView) {
            throw new MediaException.UnauthorizedException(
                    NO_ACCESS);
        }
        if (media.getLocation() == null || media.getLocation().getKey() == null) {
            throw new MediaException("Media location not configured");
        }

        Instant now = Instant.now();
        String url = storagePort.generatePresignedUrl(media.getLocation().getKey(), Duration.ofSeconds(seconds));
        return new PresignedUrl(url, seconds, now.plusSeconds(seconds));
    }

    @Override
    public int deleteMosaicItemMedia(String eventId, List<String> mediaUrls) {
        if (eventId == null || mediaUrls == null || mediaUrls.isEmpty()) {
            return 0;
        }
        List<Media> media = mediaRepository.findByEventIdAndUrls(eventId, mediaUrls);
        media.forEach(this::delete);
        log.info("Mosaic item removed in event {}: {} media file(s) deleted", eventId, media.size());
        return media.size();
    }

    private Media activeMedia(String mediaId) {
        return getMediaById(mediaId).orElseThrow(() -> new MediaException.MediaNotFoundException(mediaId));
    }

    private void delete(Media media) {
        if (media.getLocation() != null) {
            if (media.getLocation().getKey() != null) {
                storagePort.deleteFile(media.getLocation().getKey());
            }
            if (media.getLocation().getThumbnailKey() != null) {
                storagePort.deleteFile(media.getLocation().getThumbnailKey());
            }
        }
        mediaRepository.deleteById(media.getId());
        eventPublisher.publishMediaDeleted(media.getId(), media.getEventId());
    }

    @Override
    @Transactional
    public Media processMedia(String mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaException.MediaNotFoundException(mediaId));

        media.setStatus(Media.MediaStatus.PROCESSING);
        mediaRepository.save(media);

        try {
            // Process based on media type
            if (imageProcessingPort.isImage(media.getContentType())) {
                processImage(media);
            } else if (imageProcessingPort.isVideo(media.getContentType())) {
                processVideo(media);
            }

            media.setStatus(Media.MediaStatus.PROCESSED);
            media.setProcessedAt(LocalDateTime.now());
            Media saved = mediaRepository.save(media);

            eventPublisher.publishMediaProcessed(saved);
            log.info("Media processed: {}", mediaId);
            return saved;

        } catch (Exception e) {
            media.setStatus(Media.MediaStatus.FAILED);
            mediaRepository.save(media);
            throw new MediaException.ProcessingFailedException(e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new MediaException.EmptyFileException();
        }

        if (file.getSize() > maxFileSize) {
            throw new MediaException.FileTooLargeException(maxFileSize);
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedTypeList().contains(contentType)) {
            throw new MediaException.UnsupportedFileTypeException(contentType, allowedTypeList());
        }
    }

    /** El Content-Type lo pone el cliente: vale solo si coincide con el tipo real del contenido (T-B19). */
    private String verifiedContentType(String declaredType, byte[] data) {
        String detectedType = fileTypeDetector.detect(data);
        if (!allowedTypeList().contains(detectedType)) {
            throw new MediaException.UnsupportedFileTypeException(detectedType, allowedTypeList());
        }
        if (!detectedType.equals(declaredType)) {
            throw new MediaException.ContentTypeMismatchException(declaredType, detectedType, allowedTypeList());
        }
        return detectedType;
    }

    private List<String> allowedTypeList() {
        return Arrays.stream(allowedTypes.split(","))
                .map(String::trim)
                .toList();
    }

    /** La extensión sale del tipo guardado (JPEG o PNG tras re-codificar), no del nombre del cliente. */
    private String generateFileName(String contentType) {
        String extension = "image/png".equals(contentType) ? ".png" : ".jpg";
        return UUID.randomUUID().toString() + extension;
    }

    private void processImage(Media media) {
        // Image processing logic would go here
        // For now, we'll just log it
        log.debug("Processing image: {}", media.getId());
    }

    private void processVideo(Media media) {
        // Video processing logic would go here
        // This would typically involve transcoding
        log.debug("Processing video: {}", media.getId());
    }
}
