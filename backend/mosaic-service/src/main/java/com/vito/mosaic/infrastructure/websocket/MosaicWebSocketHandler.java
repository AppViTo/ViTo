package com.vito.mosaic.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.model.MosaicItem;
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
public class MosaicWebSocketHandler extends TextWebSocketHandler {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, WebSocketSession>> eventSessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String eventId = extractEventId(session);
        if (eventId != null) {
            eventSessions.computeIfAbsent(eventId, k -> new ConcurrentHashMap())
                    .put(session.getId(), session);
            log.info("WebSocket connected for mosaic event: {}, session: {}", eventId, session.getId());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String eventId = extractEventId(session);
        if (eventId != null) {
            Map<String, WebSocketSession> sessions = eventSessions.get(eventId);
            if (sessions != null) {
                sessions.remove(session.getId());
                if (sessions.isEmpty()) {
                    eventSessions.remove(eventId);
                }
            }
            log.info("WebSocket disconnected for mosaic event: {}, session: {}", eventId, session.getId());
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("Received message: {}", message.getPayload());
    }
    
    public void broadcastNewItem(String eventId, MosaicFeedItem item) {
        Map<String, WebSocketSession> sessions = eventSessions.get(eventId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "NEW_ITEM",
                    "data", item
            ));
            
            TextMessage textMessage = new TextMessage(message);
            broadcastToSessions(sessions, textMessage);
            
            log.info("Broadcasted new item to {} sessions for event: {}", sessions.size(), eventId);
        } catch (Exception e) {
            log.error("Failed to broadcast new item", e);
        }
    }
    
    public void broadcastGridUpdate(String eventId, MosaicItem item, int position) {
        Map<String, WebSocketSession> sessions = eventSessions.get(eventId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "GRID_UPDATE",
                    "data", Map.of(
                            "item", item,
                            "position", position
                    )
            ));
            
            TextMessage textMessage = new TextMessage(message);
            broadcastToSessions(sessions, textMessage);
            
            log.info("Broadcasted grid update to {} sessions for event: {}", sessions.size(), eventId);
        } catch (Exception e) {
            log.error("Failed to broadcast grid update", e);
        }
    }
    
    public void broadcastLikeUpdate(String eventId, String itemId, int likesCount) {
        Map<String, WebSocketSession> sessions = eventSessions.get(eventId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "LIKE_UPDATE",
                    "data", Map.of(
                            "itemId", itemId,
                            "likesCount", likesCount
                    )
            ));
            
            TextMessage textMessage = new TextMessage(message);
            broadcastToSessions(sessions, textMessage);
            
            log.info("Broadcasted like update to {} sessions for event: {}", sessions.size(), eventId);
        } catch (Exception e) {
            log.error("Failed to broadcast like update", e);
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
    
    private String extractEventId(WebSocketSession session) {
        String uri = session.getUri().getPath();
        String[] parts = uri.split("/");
        if (parts.length >= 3) {
            return parts[parts.length - 1];
        }
        return null;
    }
}
