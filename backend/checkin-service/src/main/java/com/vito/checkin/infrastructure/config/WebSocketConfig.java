package com.vito.checkin.infrastructure.config;

import com.vito.checkin.infrastructure.websocket.CheckInWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final CheckInWebSocketHandler checkInWebSocketHandler;

    /** Orígenes de la web (CORS_ALLOWED_ORIGINS, como el gateway). Sin Origin (app móvil) siempre pasa. */
    @Value("${websocket.allowed-origins}")
    private String[] allowedOrigins;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(checkInWebSocketHandler, "/ws/checkin/{eventId}")
                .setAllowedOrigins(allowedOrigins);
    }
}
