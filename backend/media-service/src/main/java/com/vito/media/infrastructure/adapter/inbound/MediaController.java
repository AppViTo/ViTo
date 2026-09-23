package com.vito.media.infrastructure.adapter.inbound;

import com.vito.media.application.dto.MediaResponse;
import com.vito.media.application.dto.PresignedUrlResponse;
import com.vito.media.domain.model.Media;
import com.vito.media.domain.port.inbound.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload")
    public ResponseEntity<MediaResponse> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("eventId") String eventId,
            @RequestHeader("X-User-Id") String userId) {
        Media media = mediaService.uploadMedia(file, eventId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(MediaResponse.from(media));
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<MediaResponse> getMedia(@PathVariable String mediaId) {
        return mediaService.getMediaById(mediaId)
                .map(media -> ResponseEntity.ok(MediaResponse.from(media)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<MediaResponse>> getMediaByEvent(
            @PathVariable String eventId,
            @RequestParam(required = false) Media.MediaType type) {
        List<MediaResponse> mediaList = mediaService.getMediaByEventId(eventId, type)
                .stream()
                .map(MediaResponse::from)
                .toList();
        return ResponseEntity.ok(mediaList);
    }

    @GetMapping("/user")
    public ResponseEntity<List<MediaResponse>> getMediaByUser(
            @RequestHeader("X-User-Id") String userId) {
        List<MediaResponse> mediaList = mediaService.getMediaByUserId(userId)
                .stream()
                .map(MediaResponse::from)
                .toList();
        return ResponseEntity.ok(mediaList);
    }

    @PutMapping("/{mediaId}")
    public ResponseEntity<MediaResponse> updateMedia(
            @PathVariable String mediaId,
            @RequestBody UpdateMediaRequest request,
            @RequestHeader("X-User-Id") String userId) {
        Media updated = mediaService.updateMedia(mediaId, request.fileName(), userId);
        return ResponseEntity.ok(MediaResponse.from(updated));
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> deleteMedia(
            @PathVariable String mediaId,
            @RequestHeader("X-User-Id") String userId) {
        mediaService.deleteMedia(mediaId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{mediaId}/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @PathVariable String mediaId,
            @RequestParam(defaultValue = "60") long expirationMinutes) {
        String url = mediaService.generatePresignedUrl(mediaId, expirationMinutes);
        return ResponseEntity.ok(new PresignedUrlResponse(url, expirationMinutes));
    }

    @PostMapping("/{mediaId}/process")
    public ResponseEntity<MediaResponse> processMedia(@PathVariable String mediaId) {
        Media processed = mediaService.processMedia(mediaId);
        return ResponseEntity.ok(MediaResponse.from(processed));
    }

    public record UpdateMediaRequest(String fileName) {}
}
