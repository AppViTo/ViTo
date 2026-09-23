package com.vito.chat.infrastructure.adapter.inbound;

import com.vito.chat.application.dto.CreateRoomRequest;
import com.vito.chat.application.dto.MessageResponse;
import com.vito.chat.application.dto.RoomResponse;
import com.vito.chat.application.dto.SendMessageRequest;
import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;
import com.vito.chat.domain.port.inbound.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // Room endpoints
    @PostMapping("/rooms")
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @RequestHeader("X-User-Id") String userId) {
        ChatRoom room = request.toRoom(userId);
        ChatRoom created = chatService.createRoom(room);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(created));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String roomId) {
        return chatService.getRoomById(roomId)
                .map(room -> ResponseEntity.ok(RoomResponse.from(room)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/rooms/event/{eventId}")
    public ResponseEntity<List<RoomResponse>> getRoomsByEvent(@PathVariable String eventId) {
        List<RoomResponse> rooms = chatService.getRoomsByEventId(eventId)
                .stream()
                .map(RoomResponse::from)
                .toList();
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/rooms/user")
    public ResponseEntity<List<RoomResponse>> getRoomsByUser(
            @RequestHeader("X-User-Id") String userId) {
        List<RoomResponse> rooms = chatService.getRoomsByUserId(userId)
                .stream()
                .map(RoomResponse::from)
                .toList();
        return ResponseEntity.ok(rooms);
    }

    @PostMapping("/rooms/{roomId}/join")
    public ResponseEntity<RoomResponse> joinRoom(
            @PathVariable String roomId,
            @RequestHeader("X-User-Id") String userId) {
        ChatRoom room = chatService.joinRoom(roomId, userId);
        return ResponseEntity.ok(RoomResponse.from(room));
    }

    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<RoomResponse> leaveRoom(
            @PathVariable String roomId,
            @RequestHeader("X-User-Id") String userId) {
        ChatRoom room = chatService.leaveRoom(roomId, userId);
        return ResponseEntity.ok(RoomResponse.from(room));
    }

    // Message endpoints
    @PostMapping("/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String userEmail) {
        ChatMessage message = request.toMessage(userId, userEmail);
        ChatMessage sent = chatService.sendMessage(message);
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(sent));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<MessageResponse> messages = chatService.getMessagesByRoom(roomId, page, size)
                .stream()
                .map(MessageResponse::from)
                .toList();
        return ResponseEntity.ok(messages);
    }

    @PutMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable String messageId,
            @RequestBody SendMessageRequest request,
            @RequestHeader("X-User-Id") String userId) {
        ChatMessage edited = chatService.editMessage(messageId, request.content(), userId);
        return ResponseEntity.ok(MessageResponse.from(edited));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String messageId,
            @RequestHeader("X-User-Id") String userId) {
        chatService.deleteMessage(messageId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String roomId,
            @RequestHeader("X-User-Id") String userId) {
        chatService.markMessagesAsRead(roomId, userId);
        return ResponseEntity.ok().build();
    }
}
