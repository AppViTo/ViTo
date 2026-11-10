package com.vito.media.infrastructure.adapter.inbound;

import com.vito.media.application.service.MediaServiceImpl;
import com.vito.media.domain.model.EventAccess;
import com.vito.media.domain.model.Media;
import com.vito.media.domain.port.outbound.EventAccessLookup;
import com.vito.media.domain.port.outbound.FileTypeDetectorPort;
import com.vito.media.domain.port.outbound.ImageProcessingPort;
import com.vito.media.domain.port.outbound.ImageReencoderPort;
import com.vito.media.domain.port.outbound.MediaEventPublisher;
import com.vito.media.domain.port.outbound.MediaRepository;
import com.vito.media.domain.port.outbound.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-B33: cada código de respuesta de {@code listEventMedia}, {@code deleteMedia} y
 * {@code getMediaPresignedUrl} comparado con su fixture de {@code docs/api/fixtures/media/}
 * ({@code error}, {@code status}, {@code message} y {@code details}; el timestamp varía).
 */
class MediaOla3ContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "docs", "api", "fixtures", "media");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EVENT = "evt_1";

    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final StoragePort storagePort = mock(StoragePort.class);
    private final EventAccessLookup eventAccessLookup = mock(EventAccessLookup.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MediaServiceImpl service = new MediaServiceImpl(mediaRepository, storagePort, mock(MediaEventPublisher.class),
                mock(ImageProcessingPort.class), mock(FileTypeDetectorPort.class), mock(ImageReencoderPort.class),
                eventAccessLookup);
        mvc = MockMvcBuilders.standaloneSetup(new MediaController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(eventAccessLookup.findAccess(EVENT)).thenReturn(Optional.of(new EventAccess("usr_org", null)));
        when(eventAccessLookup.findAccess("evt_x")).thenReturn(Optional.empty());
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(Media.builder().id("m1").eventId(EVENT)
                .uploadedBy("usr_owner").fileName("brindis.jpg").contentType("image/jpeg").fileSize(391204)
                .status(Media.MediaStatus.UPLOADED).uploadedAt(LocalDateTime.of(2026, 10, 16, 22, 20))
                .location(Media.S3Location.builder().bucket("vito-events").key("evt_1/brindis.jpg")
                        .url("https://cdn.vito.com/evt_1/brindis.jpg").build())
                .build()));
        when(mediaRepository.findById("m_x")).thenReturn(Optional.empty());
    }

    private static JsonNode fixture(String name) throws Exception {
        return JSON.readTree(FIXTURES.resolve(name).toFile());
    }

    /** Mismo envelope que el fixture; el texto de {@code message} también, salvo el de 404 genérico. */
    private static void matchesError(ResultActions result, String fixtureName, boolean sameMessage) throws Exception {
        JsonNode fixture = fixture(fixtureName);
        result.andExpect(status().is(fixture.get("status").asInt()))
                .andExpect(jsonPath("$.error").value(fixture.get("error").asString()))
                .andExpect(jsonPath("$.status").value(fixture.get("status").asInt()))
                .andExpect(jsonPath("$.timestamp").exists());
        if (sameMessage) {
            result.andExpect(jsonPath("$.message").value(fixture.get("message").asString()));
        }
        JsonNode details = fixture.get("details");
        if (details != null) {
            for (var field : details.properties()) {
                result.andExpect(jsonPath("$.details." + field.getKey()).value(field.getValue().asString()));
            }
        } else {
            result.andExpect(jsonPath("$.details").doesNotExist());
        }
    }

    // ---- listEventMedia

    @Test
    void listEventMedia200IsAFlatArray() throws Exception {
        Media stored = mediaRepository.findById("m1").orElseThrow();
        when(mediaRepository.findByEventId(EVENT, 0, 20)).thenReturn(List.of(stored));

        mvc.perform(get("/api/v1/media/event/{eventId}", EVENT).header("X-User-Id", "usr_org"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value("m1"))
                .andExpect(jsonPath("$[0].eventId").value(EVENT))
                .andExpect(jsonPath("$[0].location.url").value("https://cdn.vito.com/evt_1/brindis.jpg"));
    }

    @Test
    void listEventMedia400() throws Exception {
        matchesError(mvc.perform(get("/api/v1/media/event/{eventId}", EVENT).param("size", "101")
                .header("X-User-Id", "usr_org")), "listEventMedia.400.json", true);
    }

    @Test
    void listEventMedia403() throws Exception {
        matchesError(mvc.perform(get("/api/v1/media/event/{eventId}", EVENT).header("X-User-Id", "usr_other")),
                "listEventMedia.403.json", true);
    }

    @Test
    void listEventMedia404() throws Exception {
        matchesError(mvc.perform(get("/api/v1/media/event/{eventId}", "evt_x").header("X-User-Id", "usr_org")),
                "listEventMedia.404.json", true);
    }

    // ---- deleteMedia

    @Test
    void deleteMedia204() throws Exception {
        mvc.perform(delete("/api/v1/media/{mediaId}", "m1").header("X-User-Id", "usr_org"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteMedia403() throws Exception {
        matchesError(mvc.perform(delete("/api/v1/media/{mediaId}", "m1").header("X-User-Id", "usr_other")),
                "deleteMedia.403.json", true);
    }

    @Test
    void deleteMedia404() throws Exception {
        matchesError(mvc.perform(delete("/api/v1/media/{mediaId}", "m_x").header("X-User-Id", "usr_org")),
                "deleteMedia.404.json", true);
    }

    // ---- getMediaPresignedUrl

    @Test
    void getMediaPresignedUrl200HasTheFixtureShape() throws Exception {
        when(storagePort.generatePresignedUrl("evt_1/brindis.jpg", Duration.ofSeconds(900)))
                .thenReturn(fixture("getMediaPresignedUrl.200.json").get("url").asString());

        mvc.perform(get("/api/v1/media/{mediaId}/presigned-url", "m1").header("X-User-Id", "usr_org"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(fixture("getMediaPresignedUrl.200.json").get("url").asString()))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")))
                .andExpect(jsonPath("$.expirationMinutes").doesNotExist());
    }

    @Test
    void getMediaPresignedUrl400() throws Exception {
        matchesError(mvc.perform(get("/api/v1/media/{mediaId}/presigned-url", "m1").param("expiresIn", "3601")
                .header("X-User-Id", "usr_org")), "getMediaPresignedUrl.400.json", true);
    }

    @Test
    void getMediaPresignedUrl403() throws Exception {
        matchesError(mvc.perform(get("/api/v1/media/{mediaId}/presigned-url", "m1").header("X-User-Id", "usr_other")),
                "getMediaPresignedUrl.403.json", true);
    }

    @Test
    void getMediaPresignedUrl404() throws Exception {
        matchesError(mvc.perform(get("/api/v1/media/{mediaId}/presigned-url", "m_x").header("X-User-Id", "usr_org")),
                "getMediaPresignedUrl.404.json", true);
    }

    @Test
    void eventServiceDownIs503WithRetryAfter() throws Exception {
        when(eventAccessLookup.findAccess(EVENT)).thenThrow(
                new com.vito.media.domain.exception.MediaException.DependencyUnavailableException(null));

        mvc.perform(get("/api/v1/media/event/{eventId}", EVENT).header("X-User-Id", "usr_org"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Retry-After", "5"));
    }

    @Test
    void storageFailureOnDeleteIs500Envelope() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("Failed to delete file from S3"))
                .when(storagePort).deleteFile(any());

        mvc.perform(delete("/api/v1/media/{mediaId}", "m1").header("X-User-Id", "usr_owner"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
    }
}
