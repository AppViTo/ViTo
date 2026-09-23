package com.vito.archive.infrastructure.adapter.inbound.rest;

import com.vito.archive.domain.model.ArchiveComment;
import com.vito.archive.domain.model.ArchiveMedia;
import com.vito.archive.domain.model.EventArchive;
import com.vito.archive.domain.port.inbound.ArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/archive")
@RequiredArgsConstructor
public class ArchiveController {
    
    private final ArchiveService archiveService;
    
    @PostMapping
    public ResponseEntity<EventArchive> createArchive(
            @RequestBody ArchiveService.CreateArchiveCommand command) {
        String currentUserId = getCurrentUserId();
        command.setOrganizerId(currentUserId);
        
        EventArchive archive = archiveService.createArchive(command);
        return ResponseEntity.ok(archive);
    }
    
    @GetMapping("/{archiveId}")
    public ResponseEntity<EventArchive> getArchive(@PathVariable String archiveId) {
        EventArchive archive = archiveService.getArchive(archiveId);
        return ResponseEntity.ok(archive);
    }
    
    @GetMapping("/event/{eventId}")
    public ResponseEntity<EventArchive> getArchiveByEvent(@PathVariable String eventId) {
        EventArchive archive = archiveService.getArchiveByEventId(eventId);
        return ResponseEntity.ok(archive);
    }
    
    @GetMapping("/public")
    public ResponseEntity<List<EventArchive>> getPublicArchives(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<EventArchive> archives = archiveService.getPublicArchives(page, size);
        return ResponseEntity.ok(archives);
    }
    
    @GetMapping("/my")
    public ResponseEntity<List<EventArchive>> getMyArchives(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        String currentUserId = getCurrentUserId();
        List<EventArchive> archives = archiveService.getUserArchives(currentUserId, page, size);
        return ResponseEntity.ok(archives);
    }
    
    @PutMapping("/{archiveId}")
    public ResponseEntity<EventArchive> updateArchive(
            @PathVariable String archiveId,
            @RequestBody ArchiveService.UpdateArchiveCommand command) {
        
        EventArchive archive = archiveService.updateArchive(archiveId, command);
        return ResponseEntity.ok(archive);
    }
    
    @PostMapping("/{archiveId}/media")
    public ResponseEntity<ArchiveMedia> uploadMedia(
            @PathVariable String archiveId,
            @RequestBody ArchiveService.UploadMediaCommand command) {
        
        String currentUserId = getCurrentUserId();
        command.setUploaderId(currentUserId);
        
        ArchiveMedia media = archiveService.uploadMedia(archiveId, command);
        return ResponseEntity.ok(media);
    }
    
    @GetMapping("/{archiveId}/media")
    public ResponseEntity<List<ArchiveMedia>> getMedia(
            @PathVariable String archiveId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        List<ArchiveMedia> media = archiveService.getMedia(archiveId, page, size);
        return ResponseEntity.ok(media);
    }
    
    @GetMapping("/media/{mediaId}")
    public ResponseEntity<ArchiveMedia> getMediaById(@PathVariable String mediaId) {
        ArchiveMedia media = archiveService.getMediaById(mediaId);
        return ResponseEntity.ok(media);
    }
    
    @PostMapping("/{archiveId}/comments")
    public ResponseEntity<ArchiveComment> addComment(
            @PathVariable String archiveId,
            @RequestBody ArchiveService.AddCommentCommand command) {
        
        String currentUserId = getCurrentUserId();
        command.setUserId(currentUserId);
        
        ArchiveComment comment = archiveService.addComment(archiveId, command);
        return ResponseEntity.ok(comment);
    }
    
    @GetMapping("/{archiveId}/comments")
    public ResponseEntity<List<ArchiveComment>> getComments(
            @PathVariable String archiveId,
            @RequestParam(required = false) String mediaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        List<ArchiveComment> comments = archiveService.getComments(archiveId, mediaId, page, size);
        return ResponseEntity.ok(comments);
    }
    
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ArchiveComment> updateComment(
            @PathVariable String commentId,
            @RequestBody ArchiveService.AddCommentCommand command) {
        
        String currentUserId = getCurrentUserId();
        ArchiveComment comment = archiveService.updateComment(commentId, currentUserId, command.getContent());
        return ResponseEntity.ok(comment);
    }
    
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable String commentId) {
        String currentUserId = getCurrentUserId();
        archiveService.deleteComment(commentId, currentUserId);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/media/{mediaId}/like")
    public ResponseEntity<Void> likeMedia(@PathVariable String mediaId) {
        String currentUserId = getCurrentUserId();
        archiveService.likeMedia(mediaId, currentUserId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/media/{mediaId}/like")
    public ResponseEntity<Void> unlikeMedia(@PathVariable String mediaId) {
        String currentUserId = getCurrentUserId();
        archiveService.unlikeMedia(mediaId, currentUserId);
        return ResponseEntity.ok().build();
    }
    
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }
}
