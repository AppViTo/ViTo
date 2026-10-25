package com.vito.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vito.media.support.SampleFiles;
import com.vito.media.support.SampleImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-B19 por HTTP real: el multipart lo parsea Tomcat, así que también se prueba el corte de
 * {@code spring.servlet.multipart.max-file-size}. Mismas propiedades que
 * {@link MediaServiceApplicationTests} para reutilizar el contexto.
 */
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long",
        "MONGODB_URI=mongodb://localhost:27017/test_db",
        "S3_ACCESS_KEY=test",
        "S3_SECRET_KEY=test"
})
class MediaUploadValidationIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void htmlDeclaredAsPng_Returns400WithErrorEnvelope() throws Exception {
        JsonNode body = assertValidationError(upload("foto.png", "image/png", SampleFiles.html()));

        assertEquals("El tipo de archivo text/html no está permitido", body.get("message").asText());
        assertEquals("Formatos permitidos: image/jpeg, image/png, image/webp, video/mp4",
                body.get("details").get("file").asText());
    }

    @Test
    void gif_Returns400() throws Exception {
        assertValidationError(upload("animacion.gif", "image/gif", SampleFiles.gif()));
    }

    @Test
    void elevenMegabyteFile_Returns400NotServerError() throws Exception {
        JsonNode body = assertValidationError(upload("grande.jpg", "image/jpeg", new byte[11 * 1024 * 1024]));

        assertEquals("El archivo supera el tamaño máximo de 10 MB", body.get("message").asText());
    }

    /** T-B25: pasa la detección de tipo (firma PNG) pero no decodifica. */
    @Test
    void corruptImage_Returns400WithErrorEnvelope() throws Exception {
        JsonNode body = assertValidationError(upload("foto.png", "image/png", SampleImages.corruptPng()));

        assertEquals("La imagen está dañada o no se puede leer", body.get("message").asText());
        assertEquals("Sube una imagen JPEG, PNG o WebP válida", body.get("details").get("file").asText());
    }

    /** T-B25: 400 MP en menos de 1 MB; se corta con la cabecera, sin decodificar. */
    @Test
    void decompressionBomb_Returns400() throws Exception {
        JsonNode body = assertValidationError(upload("bomba.png", "image/png", SampleImages.pngBomb()));

        assertEquals("La imagen supera el máximo de 16 megapíxeles", body.get("message").asText());
    }

    /** Excepción A3: el vídeo se rechaza en esta ola (leads, 2026-10-24). */
    @Test
    void validMp4_Returns400() throws Exception {
        JsonNode body = assertValidationError(upload("video.mp4", "video/mp4", SampleFiles.mp4()));

        assertEquals("Por ahora no se admiten vídeos: sube una imagen JPEG, PNG o WebP", body.get("message").asText());
    }

    private JsonNode assertValidationError(ResponseEntity<String> response) throws Exception {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        // ErrorResponse de ADR-003: error, message, status, timestamp ISO-8601 UTC
        assertEquals("VALIDATION_ERROR", body.get("error").asText());
        assertEquals(400, body.get("status").asInt());
        assertTrue(body.get("timestamp").asText().endsWith("Z"), body.get("timestamp").asText());
        return body;
    }

    private ResponseEntity<String> upload(String fileName, String contentType, byte[] data) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        ByteArrayResource resource = new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new HttpEntity<>(resource, partHeaders));
        form.add("eventId", "evt_1");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User-Id", "usr_1");
        return http.postForEntity("http://localhost:" + port + "/api/v1/media/upload",
                new HttpEntity<>(form, headers), String.class);
    }
}
