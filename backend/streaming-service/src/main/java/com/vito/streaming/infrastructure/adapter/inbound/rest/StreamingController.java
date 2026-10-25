package com.vito.streaming.infrastructure.adapter.inbound.rest;

import com.vito.streaming.domain.model.ChatMessage;
import com.vito.streaming.domain.model.LiveStream;
import com.vito.streaming.domain.model.Reaction;
import com.vito.streaming.domain.port.inbound.StreamingService;
import com.vito.streaming.domain.exception.StreamingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/streaming")
@RequiredArgsConstructor
public class StreamingController {
    
    private final StreamingService streamingService;
    
    @PostMapping
    public ResponseEntity<LiveStream> createStream(@RequestBody StreamingService.CreateStreamCommand command) {
        String currentUserId = getCurrentUserId();
        command.setOrganizerId(currentUserId);
        
        LiveStream stream = streamingService.createStream(command);
        
        return ResponseEntity.ok(stream);
    }
    
    @PostMapping("/{streamId}/start")
    public ResponseEntity<LiveStream> startStream(@PathVariable String streamId) {
        String currentUserId = getCurrentUserId();
        LiveStream stream = streamingService.startStream(streamId, currentUserId);
        return ResponseEntity.ok(stream);
    }
    
    @PostMapping("/{streamId}/stop")
    public ResponseEntity<LiveStream> stopStream(@PathVariable String streamId) {
        String currentUserId = getCurrentUserId();
        LiveStream stream = streamingService.stopStream(streamId, currentUserId);
        return ResponseEntity.ok(stream);
    }
    
    @GetMapping("/{streamId}")
    public ResponseEntity<LiveStream> getStream(@PathVariable String streamId) {
        LiveStream stream = streamingService.getStream(streamId);
        return ResponseEntity.ok(stream);
    }
    
    @GetMapping("/event/{eventId}")
    public ResponseEntity<LiveStream> getStreamByEvent(@PathVariable String eventId) {
        LiveStream stream = streamingService.getStreamByEventId(eventId);
        return ResponseEntity.ok(stream);
    }
    
    @GetMapping("/live")
    public ResponseEntity<List<LiveStream>> getLiveStreams(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<LiveStream> streams = streamingService.getLiveStreams(page, size);
        return ResponseEntity.ok(streams);
    }
    
    @PostMapping("/{streamId}/reaction")
    public ResponseEntity<Reaction> addReaction(
            @PathVariable String streamId,
            @RequestBody StreamingService.AddReactionCommand command) {
        
        String currentUserId = getCurrentUserId();
        command.setStreamId(streamId);
        command.setUserId(currentUserId);
        
        Reaction reaction = streamingService.addReaction(command);
        
        return ResponseEntity.ok(reaction);
    }
    
    @GetMapping("/{streamId}/reactions")
    public ResponseEntity<List<Reaction>> getReactions(
            @PathVariable String streamId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        List<Reaction> reactions = streamingService.getReactions(streamId, page, size);
        return ResponseEntity.ok(reactions);
    }
    
    @PostMapping("/{streamId}/chat")
    public ResponseEntity<ChatMessage> sendChatMessage(
            @PathVariable String streamId,
            @RequestBody StreamingService.SendChatMessageCommand command) {
        
        String currentUserId = getCurrentUserId();
        command.setStreamId(streamId);
        command.setUserId(currentUserId);
        
        ChatMessage message = streamingService.sendChatMessage(command);
        
        return ResponseEntity.ok(message);
    }
    
    @GetMapping("/{streamId}/chat")
    public ResponseEntity<List<ChatMessage>> getChatMessages(
            @PathVariable String streamId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        List<ChatMessage> messages = streamingService.getChatMessages(streamId, page, size);
        return ResponseEntity.ok(messages);
    }
    
    @GetMapping("/{streamId}/info")
    public ResponseEntity<StreamingService.StreamInfo> getStreamInfo(@PathVariable String streamId) {
        StreamingService.StreamInfo info = streamingService.getStreamInfo(streamId);
        return ResponseEntity.ok(info);
    }
    
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw StreamingException.unauthenticated();
        }
        return authentication.getName();
    }
}
