package com.vito.mosaic.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-B20: el handshake de /ws/mosaic solo acepta los orígenes de {@code websocket.allowed-origins}
 * (CORS_ALLOWED_ORIGINS en producción; localhost:3000 y :8080 por defecto). Sin Origin, como
 * la app móvil, pasa siempre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketOriginTest {

    private static final String PATH = "/ws/mosaic/evt_1";

    @LocalServerPort
    int port;

    @Test
    void allowedOriginConnects() throws IOException {
        assertEquals(101, handshakeStatus("http://localhost:3000"));
    }

    @Test
    void foreignOriginGets403() throws IOException {
        assertEquals(403, handshakeStatus("https://evil.example"));
    }

    @Test
    void requestWithoutOriginConnects() throws IOException {
        assertEquals(101, handshakeStatus(null));
    }

    /** Handshake WebSocket a mano: devuelve el status HTTP (101 si se acepta el upgrade). */
    private int handshakeStatus(String origin) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            StringBuilder request = new StringBuilder()
                    .append("GET ").append(PATH).append(" HTTP/1.1\r\n")
                    .append("Host: localhost:").append(port).append("\r\n")
                    .append("Upgrade: websocket\r\n")
                    .append("Connection: Upgrade\r\n")
                    .append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
                    .append("Sec-WebSocket-Version: 13\r\n");
            if (origin != null) {
                request.append("Origin: ").append(origin).append("\r\n");
            }
            socket.getOutputStream().write(request.append("\r\n").toString().getBytes(StandardCharsets.US_ASCII));

            String statusLine = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }
}
