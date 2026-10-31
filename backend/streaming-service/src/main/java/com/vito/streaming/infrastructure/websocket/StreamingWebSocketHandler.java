package com.vito.streaming.infrastructure.websocket;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.vito.streaming.domain.model.ChatMessage;
import com.vito.streaming.domain.model.Reaction;
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
public class StreamingWebSocketHandler extends TextWebSocketHandler {
    
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final Map<String, Map<String, WebSocketSession>> streamSessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String streamId = extractStreamId(session);
        if (streamId != null) {
            streamSessions.computeIfAbsent(streamId, k -> new ConcurrentHashMap())
                    .put(session.getId(), session);
            
            // Update viewer count
            broadcastViewerCount(streamId);
            
            log.info("WebSocket connected for stream: {}, session: {}", streamId, session.getId());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String streamId = extractStreamId(session);
        if (streamId != null) {
            Map<String, WebSocketSession> sessions = streamSessions.get(streamId);
            if (sessions != null) {
                sessions.remove(session.getId());
                if (sessions.isEmpty()) {
                    streamSessions.remove(streamId);
                }
            }
            
            // Update viewer count
            broadcastViewerCount(streamId);
            
            log.info("WebSocket disconnected for stream: {}, session: {}", streamId, session.getId());
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String streamId = extractStreamId(session);
        if (streamId == null) return;
        
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");
            
            switch (type) {
                case "REACTION":
                    broadcastReaction(streamId, toModel(payload, streamId, Reaction.class));
                    break;
                case "CHAT":
                    broadcastChatMessage(streamId, toModel(payload, streamId, ChatMessage.class));
                    break;
                case "PING":
                    session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to handle WebSocket message: {}", e.getMessage());
        }
    }
    
    /**
     * Convierte el objeto "data" del mensaje entrante ({"type": "...", "data": {...}},
     * mismo formato que se emite) al modelo de dominio. El streamId siempre se toma de
     * la URL de la conexión, nunca del payload del cliente.
     */
    @SuppressWarnings("unchecked")
    private <T> T toModel(Map<String, Object> payload, String streamId, Class<T> type) {
        Map<String, Object> fields = new java.util.HashMap<>(
                payload.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : Map.of());
        fields.put("streamId", streamId);
        return objectMapper.convertValue(fields, type);
    }

    public void broadcastReaction(String streamId, Reaction reaction) {
        Map<String, WebSocketSession> sessions = streamSessions.get(streamId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "REACTION",
                    "data", reaction
            ));
            
            TextMessage textMessage = new TextMessage(message);
            broadcastToSessions(sessions, textMessage);
            
            log.debug("Broadcasted reaction to {} sessions for stream: {}", sessions.size(), streamId);
        } catch (Exception e) {
            log.error("Failed to broadcast reaction", e);
        }
    }
    
    public void broadcastChatMessage(String streamId, ChatMessage message) {
        Map<String, WebSocketSession> sessions = streamSessions.get(streamId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String jsonMessage = objectMapper.writeValueAsString(Map.of(
                    "type", "CHAT",
                    "data", message
            ));
            
            TextMessage textMessage = new TextMessage(jsonMessage);
            broadcastToSessions(sessions, textMessage);
            
            log.debug("Broadcasted chat message to {} sessions for stream: {}", sessions.size(), streamId);
        } catch (Exception e) {
            log.error("Failed to broadcast chat message", e);
        }
    }
    
    public void broadcastStreamStarted(String streamId) {
        broadcastSystemMessage(streamId, "STREAM_STARTED", "Stream is now live!");
    }
    
    public void broadcastStreamEnded(String streamId) {
        broadcastSystemMessage(streamId, "STREAM_ENDED", "Stream has ended");
    }
    
    private void broadcastSystemMessage(String streamId, String type, String content) {
        Map<String, WebSocketSession> sessions = streamSessions.get(streamId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "data", Map.of("message", content)
            ));
            
            TextMessage textMessage = new TextMessage(message);
            broadcastToSessions(sessions, textMessage);
        } catch (Exception e) {
            log.error("Failed to broadcast system message", e);
        }
    }
    
    private void broadcastViewerCount(String streamId) {
        Map<String, WebSocketSession> sessions = streamSessions.get(streamId);
        int viewerCount = sessions != null ? sessions.size() : 0;
        
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "VIEWER_COUNT",
                    "data", Map.of("count", viewerCount)
            ));
            
            TextMessage textMessage = new TextMessage(message);
            broadcastToSessions(sessions, textMessage);
        } catch (Exception e) {
            log.error("Failed to broadcast viewer count", e);
        }
    }
    
    private void broadcastToSessions(Map<String, WebSocketSession> sessions, TextMessage message) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.error("Failed to send message to session: {}", session.getId(), e);
            }
        });
    }
    
    private String extractStreamId(WebSocketSession session) {
        String uri = session.getUri().getPath();
        String[] parts = uri.split("/");
        if (parts.length >= 3) {
            return parts[parts.length - 1];
        }
        return null;
    }
}
