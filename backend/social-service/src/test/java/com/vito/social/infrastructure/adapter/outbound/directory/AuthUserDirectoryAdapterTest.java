package com.vito.social.infrastructure.adapter.outbound.directory;

import com.sun.net.httpserver.HttpServer;
import com.vito.social.domain.exception.SocialException;
import com.vito.social.domain.exception.UserDirectoryUnavailableException;
import com.vito.social.domain.model.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * T017 (Ola 2a, FR-012, FR-012a, research R2): nombre, usuario y avatar salen de
 * {@code getUsersByIds} de auth-service, con el Bearer de la petición en curso. Si auth-service
 * falla, "servicio no disponible" (503), nunca datos inventados.
 */
class AuthUserDirectoryAdapterTest {

    private static final String BASE = "http://auth-service:8081";
    private static final String CARO = "{\"userId\":\"usr_caro\",\"username\":\"caro.mz\","
            + "\"displayName\":\"Carolina Martínez\",\"avatar\":\"https://cdn.vito.com/caro.jpg\"}";
    private static final String LUIS = "{\"userId\":\"usr_luis\",\"username\":\"luis\",\"displayName\":null,\"avatar\":null}";

    private MockRestServiceServer server;
    private AuthUserDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AuthUserDirectoryAdapter(builder.baseUrl(BASE).build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-ana");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void asksAuthServiceForwardingTheBearerAndKeepsTheRequestedOrder() {
        server.expect(once(), requestTo(BASE + "/api/v1/users?ids=usr_caro,usr_luis"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-ana"))
                .andExpect(queryParam("ids", "usr_caro,usr_luis"))
                .andRespond(withSuccess("[" + CARO + "," + LUIS + "]", MediaType.APPLICATION_JSON));

        List<UserSummary> users = adapter.findByIds(List.of("usr_caro", "usr_luis"));

        assertEquals(List.of(
                new UserSummary("usr_caro", "caro.mz", "Carolina Martínez", "https://cdn.vito.com/caro.jpg"),
                new UserSummary("usr_luis", "luis", null, null)), users);
        server.verify();
    }

    /** El orden es el pedido aunque auth-service respondiera en otro. */
    @Test
    void reordersToTheRequestedOrder() {
        server.expect(requestTo(BASE + "/api/v1/users?ids=usr_luis,usr_caro"))
                .andRespond(withSuccess("[" + CARO + "," + LUIS + "]", MediaType.APPLICATION_JSON));

        List<UserSummary> users = adapter.findByIds(List.of("usr_luis", "usr_caro"));

        assertEquals(List.of("usr_luis", "usr_caro"), users.stream().map(UserSummary::userId).toList());
    }

    /** Un id que auth-service ya no conoce se omite (FR-012a), no rompe la página. */
    @Test
    void unknownIdsAreOmitted() {
        server.expect(requestTo(BASE + "/api/v1/users?ids=usr_caro,usr_borrado"))
                .andRespond(withSuccess("[" + CARO + "]", MediaType.APPLICATION_JSON));

        List<UserSummary> users = adapter.findByIds(List.of("usr_caro", "usr_borrado"));

        assertEquals(List.of("usr_caro"), users.stream().map(UserSummary::userId).toList());
    }

    @Test
    void emptyResponseIsAnEmptyList() {
        server.expect(requestTo(BASE + "/api/v1/users?ids=usr_borrado"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertEquals(List.of(), adapter.findByIds(List.of("usr_borrado")));
    }

    /** Sin ids no hay llamada: el servidor simulado falla ante cualquier petición inesperada. */
    @Test
    void noIdsMeansNoCall() {
        assertEquals(List.of(), adapter.findByIds(List.of()));
        server.verify();
    }

    @Test
    void repeatedIdsAreAskedOnce() {
        server.expect(once(), requestTo(BASE + "/api/v1/users?ids=usr_caro,usr_luis"))
                .andRespond(withSuccess("[" + CARO + "," + LUIS + "]", MediaType.APPLICATION_JSON));

        List<UserSummary> users = adapter.findByIds(List.of("usr_caro", "usr_luis", "usr_caro"));

        assertEquals(List.of("usr_caro", "usr_luis"), users.stream().map(UserSummary::userId).toList());
        server.verify();
    }

    /** getUsersByIds acepta hasta 50: una página de social nunca pasa de 50, pero por si acaso. */
    @Test
    void moreThanFiftyIdsAreSplitInBatches() {
        List<String> ids = IntStream.range(0, 60).mapToObj(i -> "usr_" + i).toList();
        server.expect(once(), requestTo(BASE + "/api/v1/users?ids=" + String.join(",", ids.subList(0, 50))))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/api/v1/users?ids=" + String.join(",", ids.subList(50, 60))))
                .andRespond(withSuccess("[" + summary("usr_55") + "]", MediaType.APPLICATION_JSON));

        assertEquals(List.of("usr_55"), adapter.findByIds(ids).stream().map(UserSummary::userId).toList());
        server.verify();
    }

    // ── fallos (FR-012a) ────────────────────────────────────────

    @Test
    void serverErrorIsUnavailable() {
        server.expect(requestTo(BASE + "/api/v1/users?ids=usr_caro")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(UserDirectoryUnavailableException.class, () -> adapter.findByIds(List.of("usr_caro")));
    }

    @Test
    void timeoutIsUnavailable() {
        server.expect(requestTo(BASE + "/api/v1/users?ids=usr_caro"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThrows(UserDirectoryUnavailableException.class, () -> adapter.findByIds(List.of("usr_caro")));
    }

    @Test
    void unreadableBodyIsUnavailable() {
        server.expect(requestTo(BASE + "/api/v1/users?ids=usr_caro"))
                .andRespond(withSuccess("<html>proxy</html>", MediaType.TEXT_HTML));

        assertThrows(UserDirectoryUnavailableException.class, () -> adapter.findByIds(List.of("usr_caro")));
    }

    /** Un 400 sería un error nuestro: tampoco se convierte en un 500 genérico. */
    @Test
    void unexpectedClientErrorIsUnavailable() {
        server.expect(requestTo(BASE + "/api/v1/users?ids=usr_caro")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(UserDirectoryUnavailableException.class, () -> adapter.findByIds(List.of("usr_caro")));
    }

    /** El token venció entre el gateway y aquí: 401, no 503. */
    @Test
    void unauthorizedIsPropagatedAs401() {
        server.expect(requestTo(BASE + "/api/v1/users?ids=usr_caro")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        SocialException error = assertThrows(SocialException.class, () -> adapter.findByIds(List.of("usr_caro")));

        assertEquals(SocialException.Reason.UNAUTHORIZED, error.getReason());
        assertEquals("El token de acceso es inválido o ha expirado", error.getMessage());
    }

    // ── timeouts reales (research R2: conexión 1 s, lectura 2 s) ─

    @Test
    void slowAuthServiceIsCutAfterTheReadTimeout() throws IOException {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/api/v1/users", exchange -> {
            try {
                Thread.sleep(3_000);
                byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        slow.start();
        try {
            AuthUserDirectoryAdapter real = new AuthUserDirectoryAdapter(RestClient.builder(),
                    "http://127.0.0.1:" + slow.getAddress().getPort());

            long start = System.nanoTime();
            assertTimeoutPreemptively(Duration.ofMillis(2_800), () ->
                    assertThrows(UserDirectoryUnavailableException.class, () -> real.findByIds(List.of("usr_caro"))));
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertTrue(elapsedMs >= 1_900, "cortó antes de los 2 s de lectura: " + elapsedMs + " ms");
        } finally {
            slow.stop(0);
        }
    }

    private static String summary(String id) {
        return "{\"userId\":\"" + id + "\",\"username\":\"" + id + "\",\"displayName\":null,\"avatar\":null}";
    }
}
