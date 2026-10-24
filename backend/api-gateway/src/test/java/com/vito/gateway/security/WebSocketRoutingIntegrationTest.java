package com.vito.gateway.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Handshake real a través del gateway hacia un backend WS falso que responde con la identidad
 * y la query que recibió (T-B04, CONVENTIONS §7, ADR-006).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketRoutingIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final DisposableServer backend = HttpServer.create()
            .port(0)
            .route(routes -> routes.get("/ws/checkin/{eventId}", (request, response) ->
                    response.sendWebsocket((in, out) -> out.sendString(Mono.just(
                            request.requestHeaders().get("X-User-Id") + "|" + request.uri())))))
            .bindNow();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
        registry.add("spring.cloud.gateway.routes[0].id", () -> "checkin-ws");
        // http como en application.yml: el gateway la convierte a ws:// ante un Upgrade: websocket.
        // 127.0.0.1 y no localhost: Reactor Netty resuelve localhost con su cliente DNS y en el
        // runner de CI a veces recibe SERVFAIL (el test fallaba de forma intermitente)
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://127.0.0.1:" + backend.port());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/ws/checkin/**");
    }

    @AfterAll
    static void stopBackend() {
        backend.disposeNow();
    }

    @LocalServerPort
    int port;

    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

    @Test
    void validTokenReachesServiceWithGatewayIdentityAndWithoutToken() {
        AtomicReference<String> reply = new AtomicReference<>();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "victim");

        client.execute(URI.create("ws://127.0.0.1:" + port + "/ws/checkin/evt_1?token=" + JwtAuthenticationFilterTest.token("usr_real")),
                        headers,
                        session -> session.receive()
                                .take(1)
                                .map(WebSocketMessage::getPayloadAsText)
                                .doOnNext(reply::set)
                                .then())
                .block(TIMEOUT);

        assertEquals("usr_real|/ws/checkin/evt_1", reply.get());
    }

    @Test
    void invalidTokenIsClosedWith4401() {
        AtomicReference<CloseStatus> status = new AtomicReference<>();

        client.execute(URI.create("ws://127.0.0.1:" + port + "/ws/checkin/evt_1?token=forged"),
                        session -> session.closeStatus()
                                .doOnNext(status::set)
                                .and(session.receive().then()))
                .block(TIMEOUT);

        assertNotNull(status.get());
        assertEquals(4401, status.get().getCode());
    }
}
