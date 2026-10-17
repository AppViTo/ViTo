package com.vito.mosaic.infrastructure.config;

import com.vito.mosaic.infrastructure.websocket.MosaicWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket crudo (ADR-006). El cliente entra por el gateway (/ws/mosaic/{eventId}?token=),
 * que valida el token y reenvía la identidad en X-User-*.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final MosaicWebSocketHandler mosaicWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mosaicWebSocketHandler, "/ws/mosaic/{eventId}")
                .setAllowedOrigins("*");
    }
}
