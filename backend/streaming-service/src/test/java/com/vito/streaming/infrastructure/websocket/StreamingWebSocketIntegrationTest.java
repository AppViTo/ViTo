package com.vito.streaming.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** T-B05: /ws/streaming/{streamId} está registrado como WebSocket crudo (ADR-006). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StreamingWebSocketIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    int port;

    @Test
    void viewerGetsViewerCountOnConnectAndPongOnPing() throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                        frames.add(message.getPayload());
                    }
                }, "ws://localhost:" + port + "/ws/streaming/str_1")
                .get(5, TimeUnit.SECONDS);

        assertEquals(JSON.readTree("{\"type\":\"VIEWER_COUNT\",\"data\":{\"count\":1}}"),
                JSON.readTree(frames.poll(5, TimeUnit.SECONDS)));

        session.sendMessage(new TextMessage("{\"type\":\"PING\"}"));
        assertEquals("{\"type\":\"PONG\"}", frames.poll(5, TimeUnit.SECONDS));

        session.close();
    }
}
