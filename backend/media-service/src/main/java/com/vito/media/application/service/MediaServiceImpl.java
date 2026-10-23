package com.vito.media.application.service;

import com.vito.media.domain.exception.MediaException;
import com.vito.media.domain.model.Media;
import com.vito.media.domain.port.inbound.MediaService;
import com.vito.media.domain.port.outbound.FileTypeDetectorPort;
import com.vito.media.domain.port.outbound.ImageProcessingPort;
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

            String fileName = generateFileName(file.getOriginalFilename());
            String key = String.format("events/%s/%s/%s", eventId, userId, fileName);

            // Upload to S3
            String url = storagePort.uploadFile(key, data, contentType);

            // Extract metadata for images
            Media.MediaMetadata metadata = null;
            if (imageProcessingPort.isImage(contentType)) {
                metadata = imageProcessingPort.extractMetadata(data, contentType);
            }

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
                    .fileSize(file.getSize())
                    .status(Media.MediaStatus.UPLOADED)
                    .metadata(metadata)
                    .location(locationBuilder.build())
                    .uploadedAt(LocalDateTime.now())
                    .deleted(false)
                    .build();

            Media saved = mediaRepository.save(media);
            eventPublisher.publishMediaUploaded(saved);

            log.info("Media uploaded: {} by user {} for event {}", saved.getId(), userId, eventId);
            return saved;

        } catch (IOException e) {
            throw new MediaException.UploadFailedException(e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Media> getMediaById(String mediaId) {
        return mediaRepository.findById(mediaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Media> getMediaByEventId(String eventId, Media.MediaType type) {
        if (type != null) {
            return mediaRepository.findByEventIdAndType(eventId, type);
        }
        return mediaRepository.findByEventId(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Media> getMediaByUserId(String userId) {
        return mediaRepository.findByUploadedBy(userId);
    }

    @Override
    @Transactional
    public Media updateMedia(String mediaId, String fileName, String userId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaException.MediaNotFoundException(mediaId));

        if (!media.getUploadedBy().equals(userId)) {
            throw new MediaException.UnauthorizedException("Can only update your own media");
        }

        media.setFileName(fileName);
        Media updated = mediaRepository.save(media);

        log.info("Media updated: {} by user {}", mediaId, userId);
        return updated;
    }

    @Override
    @Transactional
    public void deleteMedia(String mediaId, String userId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaException.MediaNotFoundException(mediaId));

        if (!media.getUploadedBy().equals(userId)) {
            throw new MediaException.UnauthorizedException("Can only delete your own media");
        }

        // Delete from S3
        if (media.getLocation() != null) {
            storagePort.deleteFile(media.getLocation().getKey());
            if (media.getLocation().getThumbnailKey() != null) {
                storagePort.deleteFile(media.getLocation().getThumbnailKey());
            }
        }

        media.setDeleted(true);
        mediaRepository.save(media);

        eventPublisher.publishMediaDeleted(mediaId, media.getEventId());
        log.info("Media deleted: {} by user {}", mediaId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public String generatePresignedUrl(String mediaId, long expirationMinutes) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaException.MediaNotFoundException(mediaId));

        if (media.getLocation() == null || media.getLocation().getKey() == null) {
            throw new MediaException("Media location not configured");
        }

        return storagePort.generatePresignedUrl(media.getLocation().getKey(), expirationMinutes);
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

    private String generateFileName(String originalFileName) {
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
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
