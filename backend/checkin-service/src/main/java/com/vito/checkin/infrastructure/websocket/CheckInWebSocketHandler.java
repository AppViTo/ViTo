package com.vito.checkin.infrastructure.websocket;

import tools.jackson.databind.ObjectMapper;
import com.vito.checkin.domain.model.CheckInEvent;
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
public class CheckInWebSocketHandler extends TextWebSocketHandler {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, WebSocketSession>> eventSessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String eventId = extractEventId(session);
        if (eventId != null) {
            eventSessions.computeIfAbsent(eventId, k -> new ConcurrentHashMap())
                    .put(session.getId(), session);
            log.info("WebSocket connected for event: {}, session: {}", eventId, session.getId());
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
            log.info("WebSocket disconnected for event: {}, session: {}", eventId, session.getId());
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Handle incoming messages if needed
        log.debug("Received message: {}", message.getPayload());
    }
    
    public void broadcastCheckIn(String eventId, CheckInEvent checkInEvent) {
        Map<String, WebSocketSession> sessions = eventSessions.get(eventId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "CHECK_IN",
                    "data", checkInEvent
            ));
            
            TextMessage textMessage = new TextMessage(message);
            
            sessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("Failed to send message to session: {}", session.getId(), e);
                }
            });
            
            log.info("Broadcasted check-in event to {} sessions for event: {}", 
                    sessions.size(), eventId);
        } catch (Exception e) {
            log.error("Failed to broadcast check-in event", e);
        }
    }
    
    public void broadcastStats(String eventId, int totalCheckedIn, int totalExpected) {
        Map<String, WebSocketSession> sessions = eventSessions.get(eventId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "STATS_UPDATE",
                    "data", Map.of(
                            "eventId", eventId,
                            "totalCheckedIn", totalCheckedIn,
                            "totalExpected", totalExpected,
                            "checkInRate", totalExpected > 0 ? 
                                    (double) totalCheckedIn / totalExpected * 100 : 0
                    )
            ));
            
            TextMessage textMessage = new TextMessage(message);
            
            sessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("Failed to send message to session: {}", session.getId(), e);
                }
            });
            
            log.info("Broadcasted stats update to {} sessions for event: {}", 
                    sessions.size(), eventId);
        } catch (Exception e) {
            log.error("Failed to broadcast stats update", e);
        }
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
