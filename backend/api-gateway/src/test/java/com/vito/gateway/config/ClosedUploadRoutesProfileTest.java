package com.vito.gateway.config;

import com.vito.gateway.error.ErrorResponseContract;
import com.vito.gateway.security.JwtAuthenticationFilterTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-I17 (excepción A3): en {@code prod} la subida a media y la escritura en mosaic responden
 * {@code 404} sin llegar al servicio; en {@code docker} se enrutan. Los perfiles son los de
 * docker-compose.prod.yml ({@code prod,docker}) y del compose local ({@code docker}).
 *
 * <p>T-I21: con {@code MOSAIC_UPLOADS_ENABLED=true} se abren en {@code prod} la subida y las
 * escrituras del mosaico de la Ola 3; las demás escrituras siguen en {@code 404}.
 */
class ClosedUploadRoutesProfileTest {

    private static final AtomicInteger backendHits = new AtomicInteger();

    /** Hace de media-service y mosaic-service: responde 200 a todo y cuenta lo que le llega. */
    private static final DisposableServer backend = HttpServer.create()
            .port(0)
            .handle((request, response) -> {
                backendHits.incrementAndGet();
                return request.receive().then(response.status(200)
                        .header("Content-Type", "application/json")
                        .sendString(Mono.just("{\"routed\":true}")).then());
            })
            .bindNow();

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JwtAuthenticationFilterTest.SECRET);
        registry.add("MEDIA_SERVICE_URI", () -> "http://localhost:" + backend.port());
        registry.add("MOSAIC_SERVICE_URI", () -> "http://localhost:" + backend.port());
    }

    @AfterAll
    static void stopBackend() {
        backend.disposeNow();
    }

    static WebTestClient.ResponseSpec send(WebTestClient client, HttpMethod method, String path) {
        return client.method(method).uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtAuthenticationFilterTest.token("usr_1"))
                .exchange();
    }

    static void assertClosed(WebTestClient client, HttpMethod method, String path) {
        String body = send(client, method, path)
                .expectStatus().isNotFound()
                .expectBody(String.class).returnResult().getResponseBody();
        ErrorResponseContract.assertMatches(body, 404, "NOT_FOUND");
        assertEquals(0, backendHits.get(), method + " " + path + " llegó al servicio");
    }

    static void assertRouted(WebTestClient client, HttpMethod method, String path) {
        send(client, method, path).expectStatus().isOk();
        assertEquals(1, backendHits.get(), method + " " + path + " no llegó al servicio");
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebTestClient
    @ActiveProfiles({"prod", "docker"})
    class ProdProfile {
        @Autowired
        WebTestClient client;

        @BeforeEach
        void resetHits() {
            backendHits.set(0);
        }

        @Test
        void mediaUploadIsClosed() {
            assertClosed(client, HttpMethod.POST, "/api/v1/media/upload");
            assertClosed(client, HttpMethod.POST, "/api/v1/media/upload/");
            assertClosed(client, HttpMethod.POST, "/api/v1/media/med_1/process");
        }

        @Test
        void mediaUploadIsClosedEvenWithoutToken() {
            String body = client.post().uri("/api/v1/media/upload").exchange()
                    .expectStatus().isNotFound()
                    .expectBody(String.class).returnResult().getResponseBody();
            // Sin 401: no revela que la ruta existe
            ErrorResponseContract.assertMatches(body, 404, "NOT_FOUND");
        }

        @Test
        void mosaicWritesAreClosed() {
            assertClosed(client, HttpMethod.POST, "/api/v1/mosaic/photo");
            assertClosed(client, HttpMethod.POST, "/api/v1/mosaic/post");
            assertClosed(client, HttpMethod.DELETE, "/api/v1/mosaic/item/itm_1/like");
            assertClosed(client, HttpMethod.PUT, "/api/v1/mosaic/item/itm_1");
            assertClosed(client, HttpMethod.PATCH, "/api/v1/mosaic/item/itm_1");
        }

        /** T-I21 con la propiedad apagada (valor por defecto): las rutas de la Ola 3 siguen cerradas. */
        @Test
        void olaTresWritesAreClosedByDefault() {
            assertClosed(client, HttpMethod.POST, "/api/v1/mosaic/grid/evt_1/initialize");
            assertClosed(client, HttpMethod.POST, "/api/v1/mosaic/item/itm_1/like");
            assertClosed(client, HttpMethod.DELETE, "/api/v1/mosaic/item/itm_1");
        }

        @Test
        void readsAreStillRouted() {
            assertRouted(client, HttpMethod.GET, "/api/v1/media/med_1");
            backendHits.set(0);
            assertRouted(client, HttpMethod.GET, "/api/v1/mosaic/feed/evt_1");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebTestClient
    @ActiveProfiles("docker")
    class DockerProfile {
        @Autowired
        WebTestClient client;

        @BeforeEach
        void resetHits() {
            backendHits.set(0);
        }

        @Test
        void mediaUploadIsRouted() {
            assertRouted(client, HttpMethod.POST, "/api/v1/media/upload");
        }

        @Test
        void mosaicWritesAreRouted() {
            assertRouted(client, HttpMethod.POST, "/api/v1/mosaic/photo");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "MOSAIC_UPLOADS_ENABLED=true")
    @AutoConfigureWebTestClient
    @ActiveProfiles({"prod", "docker"})
    class ProdProfileWithUploadsEnabled {
        @Autowired
        WebTestClient client;

        @BeforeEach
        void resetHits() {
            backendHits.set(0);
        }

        @Test
        void mediaUploadIsRouted() {
            assertRouted(client, HttpMethod.POST, "/api/v1/media/upload");
        }

        @Test
        void olaTresMosaicWritesAreRouted() {
            assertRouted(client, HttpMethod.POST, "/api/v1/mosaic/post");
            backendHits.set(0);
            assertRouted(client, HttpMethod.POST, "/api/v1/mosaic/grid/evt_1/initialize");
            backendHits.set(0);
            assertRouted(client, HttpMethod.POST, "/api/v1/mosaic/item/itm_1/like");
            backendHits.set(0);
            assertRouted(client, HttpMethod.DELETE, "/api/v1/mosaic/item/itm_1/like");
        }

        /** La moderación del organizador (removeMosaicItem) abre junto con la subida: A3 exige las dos. */
        @Test
        void organizerModerationIsRouted() {
            assertRouted(client, HttpMethod.DELETE, "/api/v1/mosaic/item/itm_1");
        }

        /** ADR-016 elimina photo y checkin; process es interno (transcode vía Kafka). */
        @Test
        void otherWritesStayClosed() {
            assertClosed(client, HttpMethod.POST, "/api/v1/mosaic/photo");
            assertClosed(client, HttpMethod.POST, "/api/v1/mosaic/checkin");
            assertClosed(client, HttpMethod.POST, "/api/v1/media/med_1/process");
            assertClosed(client, HttpMethod.POST, "/api/v1/media/upload/");
            assertClosed(client, HttpMethod.PUT, "/api/v1/mosaic/item/itm_1/like");
            assertClosed(client, HttpMethod.PUT, "/api/v1/mosaic/item/itm_1");
            assertClosed(client, HttpMethod.PATCH, "/api/v1/mosaic/item/itm_1");
            assertClosed(client, HttpMethod.POST, "/api/v1/mosaic/item/itm_1");
            assertClosed(client, HttpMethod.POST, "/api/v1/mosaic/grid/evt_1");
        }

        @Test
        void readsAreStillRouted() {
            assertRouted(client, HttpMethod.GET, "/api/v1/mosaic/feed/evt_1");
        }
    }
}
