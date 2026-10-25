package com.vito.mosaic.infrastructure.adapter.inbound.rest;

import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicGrid;
import com.vito.mosaic.domain.port.inbound.MosaicService;
import com.vito.mosaic.domain.exception.MosaicException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/mosaic")
@RequiredArgsConstructor
public class MosaicController {
    
    private final MosaicService mosaicService;
    
    @PostMapping("/post")
    public ResponseEntity<MosaicFeedItem> addPost(@RequestBody MosaicService.AddPostCommand command) {
        String currentUserId = getCurrentUserId();
        String username = getCurrentUsername();
        
        command.setUserId(currentUserId);
        command.setUsername(username);
        
        MosaicFeedItem item = mosaicService.addPost(command);
        
        return ResponseEntity.ok(item);
    }
    
    @PostMapping("/photo")
    public ResponseEntity<MosaicFeedItem> addPhoto(@RequestBody MosaicService.AddPhotoCommand command) {
        String currentUserId = getCurrentUserId();
        String username = getCurrentUsername();
        
        command.setUserId(currentUserId);
        command.setUsername(username);
        
        MosaicFeedItem item = mosaicService.addPhoto(command);
        
        return ResponseEntity.ok(item);
    }
    
    @PostMapping("/checkin")
    public ResponseEntity<MosaicFeedItem> addCheckIn(@RequestBody MosaicService.AddCheckInCommand command) {
        String currentUserId = getCurrentUserId();
        String username = getCurrentUsername();
        
        command.setUserId(currentUserId);
        command.setUsername(username);
        
        MosaicFeedItem item = mosaicService.addCheckIn(command);
        
        return ResponseEntity.ok(item);
    }
    
    @GetMapping("/feed/{eventId}")
    public ResponseEntity<List<MosaicFeedItem>> getEventFeed(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<MosaicFeedItem> feed = mosaicService.getEventFeed(eventId, page, size);
        
        return ResponseEntity.ok(feed);
    }
    
    @GetMapping("/grid/{eventId}")
    public ResponseEntity<MosaicGrid> getEventGrid(@PathVariable String eventId) {
        MosaicGrid grid = mosaicService.getEventGrid(eventId);
        return ResponseEntity.ok(grid);
    }
    
    @PostMapping("/grid/{eventId}/initialize")
    public ResponseEntity<MosaicGrid> initializeGrid(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "4") int rows,
            @RequestParam(defaultValue = "4") int cols) {
        
        MosaicGrid grid = mosaicService.initializeGrid(eventId, rows, cols);
        return ResponseEntity.ok(grid);
    }
    
    @PostMapping("/item/{itemId}/like")
    public ResponseEntity<Void> likePost(@PathVariable String itemId) {
        String currentUserId = getCurrentUserId();
        mosaicService.likePost(itemId, currentUserId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/item/{itemId}/like")
    public ResponseEntity<Void> unlikePost(@PathVariable String itemId) {
        String currentUserId = getCurrentUserId();
        mosaicService.unlikePost(itemId, currentUserId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/item/{itemId}")
    public ResponseEntity<MosaicFeedItem> getFeedItem(@PathVariable String itemId) {
        MosaicFeedItem item = mosaicService.getFeedItem(itemId);
        return ResponseEntity.ok(item);
    }

    /** removeMosaicItem (T-B26): solo el organizador del evento; {@code 204} sin cuerpo. */
    @DeleteMapping("/item/{itemId}")
    public ResponseEntity<Void> removeMosaicItem(@PathVariable String itemId) {
        mosaicService.removeItem(itemId, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
    
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw MosaicException.unauthenticated();
        }
        return authentication.getName();
    }
    
    private String getCurrentUsername() {
        // In production, fetch from user service
        return "user_" + getCurrentUserId().substring(0, Math.min(8, getCurrentUserId().length()));
    }
}
