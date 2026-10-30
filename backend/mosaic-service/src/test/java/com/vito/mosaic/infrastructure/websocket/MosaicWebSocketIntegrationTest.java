package com.vito.mosaic.infrastructure.websocket;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/** T-B05: /ws/mosaic/{eventId} está registrado como WebSocket crudo (ADR-006). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MosaicWebSocketIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    int port;

    @Autowired
    MosaicWebSocketHandler handler;

    @Test
    void clientOnEventChannelReceivesBroadcastFrames() throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                        frames.add(message.getPayload());
                    }
                }, "ws://localhost:" + port + "/ws/mosaic/evt_1")
                .get(5, TimeUnit.SECONDS);

        // El servidor registra la sesión justo después del handshake: se reintenta hasta recibir
        String frame = null;
        for (int i = 0; i < 50 && frame == null; i++) {
            handler.broadcastLikeUpdate("evt_1", "itm_1", 3);
            frame = frames.poll(100, TimeUnit.MILLISECONDS);
        }
        session.close();

        assertEquals(JSON.readTree("{\"type\":\"LIKE_UPDATE\",\"data\":{\"itemId\":\"itm_1\",\"likesCount\":3}}"),
                JSON.readTree(frame));
    }

    /** T-B26: ITEM_REMOVED es plano, como {@code MosaicItemRemoved} de asyncapi.yaml. */
    @Test
    void organizerRemovalReachesClientsAsFlatItemRemovedFrame() throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                        frames.add(message.getPayload());
                    }
                }, "ws://localhost:" + port + "/ws/mosaic/evt_removed")
                .get(5, TimeUnit.SECONDS);

        String frame = null;
        for (int i = 0; i < 50 && frame == null; i++) {
            handler.itemRemoved("evt_removed", "itm_9");
            frame = frames.poll(100, TimeUnit.MILLISECONDS);
        }
        session.close();

        assertEquals(JSON.readTree("{\"type\":\"ITEM_REMOVED\",\"eventId\":\"evt_removed\",\"itemId\":\"itm_9\"}"),
                JSON.readTree(frame));
    }
}
