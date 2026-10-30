package com.vito.chat.infrastructure.websocket;

import tools.jackson.databind.ObjectMapper;
import com.vito.chat.application.dto.SendMessageRequest;
import com.vito.chat.application.dto.TypingIndicatorRequest;
import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.port.inbound.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
        if (userId != null) {
            sessions.put(session.getId(), session);
            userSessions.put(userId, session.getId());
            log.info("WebSocket connected: session={}, user={}", session.getId(), userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = extractUserId(session);
        if (userId == null) {
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            switch (type) {
                case "JOIN_ROOM" -> handleJoinRoom(session, payload, userId);
                case "LEAVE_ROOM" -> handleLeaveRoom(session, payload, userId);
                case "SEND_MESSAGE" -> handleSendMessage(session, payload, userId);
                case "TYPING" -> handleTypingIndicator(session, payload, userId);
                case "MARK_READ" -> handleMarkRead(session, payload, userId);
                default -> log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage(), e);
            sendError(session, "Invalid message format");
        }
    }

    private void handleJoinRoom(WebSocketSession session, Map<String, Object> payload, String userId) {
        String roomId = (String) payload.get("roomId");
        chatService.joinRoom(roomId, userId);
        sendJson(session, Map.of("type", "ROOM_JOINED", "roomId", roomId));
    }

    private void handleLeaveRoom(WebSocketSession session, Map<String, Object> payload, String userId) {
        String roomId = (String) payload.get("roomId");
        chatService.leaveRoom(roomId, userId);
        sendJson(session, Map.of("type", "ROOM_LEFT", "roomId", roomId));
    }

    private void handleSendMessage(WebSocketSession session, Map<String, Object> payload, String userId) {
        String roomId = (String) payload.get("roomId");
        String content = (String) payload.get("content");
        String mediaUrl = (String) payload.get("mediaUrl");
        String typeStr = (String) payload.get("messageType");

        ChatMessage.MessageType messageType = typeStr != null 
                ? ChatMessage.MessageType.valueOf(typeStr) 
                : ChatMessage.MessageType.TEXT;

        SendMessageRequest request = new SendMessageRequest(roomId, content, messageType, mediaUrl);
        ChatMessage message = request.toMessage(userId, "User " + userId);
        ChatMessage saved = chatService.sendMessage(message);
        
        sendJson(session, Map.of("type", "MESSAGE_SENT", "message", saved));
    }

    private void handleTypingIndicator(WebSocketSession session, Map<String, Object> payload, String userId) {
        String roomId = (String) payload.get("roomId");
        boolean isTyping = Boolean.TRUE.equals(payload.get("isTyping"));
        chatService.sendTypingIndicator(roomId, userId, isTyping);
    }

    private void handleMarkRead(WebSocketSession session, Map<String, Object> payload, String userId) {
        String roomId = (String) payload.get("roomId");
        chatService.markMessagesAsRead(roomId, userId);
        sendJson(session, Map.of("type", "MESSAGES_READ", "roomId", roomId));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractUserId(session);
        if (userId != null) {
            sessions.remove(session.getId());
            userSessions.remove(userId);
            log.info("WebSocket disconnected: session={}, user={}", session.getId(), userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: session={}, error={}", session.getId(), exception.getMessage());
    }

    private String extractUserId(WebSocketSession session) {
        return (String) session.getAttributes().get("userId");
    }

    private void sendJson(WebSocketSession session, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send WebSocket message: {}", e.getMessage(), e);
        }
    }

    private void sendError(WebSocketSession session, String message) {
        sendJson(session, Map.of("type", "ERROR", "message", message));
    }

    public void broadcastToRoom(String roomId, Object data) {
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                String userId = extractUserId(session);
                if (userId != null) {
                    sendJson(session, data);
                }
            }
        });
    }
}
