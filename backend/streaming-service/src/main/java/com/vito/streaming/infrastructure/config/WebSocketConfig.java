package com.vito.streaming.infrastructure.config;

import com.vito.streaming.infrastructure.websocket.StreamingWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket crudo (ADR-006). El cliente entra por el gateway (/ws/streaming/{streamId}?token=),
 * que valida el token y reenvía la identidad en X-User-*.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final StreamingWebSocketHandler streamingWebSocketHandler;

    /** Orígenes de la web (CORS_ALLOWED_ORIGINS, como el gateway). Sin Origin (app móvil) siempre pasa. */
    @Value("${websocket.allowed-origins}")
    private String[] allowedOrigins;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(streamingWebSocketHandler, "/ws/streaming/{streamId}")
                .setAllowedOrigins(allowedOrigins);
    }
}
