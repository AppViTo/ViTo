package com.vito.media.infrastructure.adapter.outbound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-B25: el peor caso de la re-codificación cabe en el heap de media-service en producción.
 * Lee {@code mem_limit} y {@code MaxRAMPercentage} de {@code docker-compose.prod.yml}: si alguien
 * baja la memoria o sube el tope de píxeles sin revisar el otro, este test falla.
 */
class ImageMemoryBudgetTest {

    private static final long MB = 1024L * 1024;

    /** Lo que usa el resto del servicio (Spring, Kafka, Mongo, buffers de Tomcat) en reposo y carga. */
    private static final long SERVICE_RESERVE = 128 * MB;

    /** El archivo subido, su copia en memoria y la salida re-codificada: hasta 10 MB cada uno, con margen. */
    private static final long UPLOAD_BUFFERS = 3 * 10 * MB;

    @Test
    void worstCaseReencodeFitsInTheProductionHeap() throws IOException {
        String compose = Files.readString(composeFile());
        String mediaService = section(compose, "  media-service:");
        long memLimit = megabytes(firstMatch(mediaService.contains("mem_limit")
                ? mediaService : compose, "mem_limit:\\s*(\\d+)m"));
        long ramPercentage = Long.parseLong(firstMatch(mediaService.contains("MaxRAMPercentage")
                ? mediaService : compose, "MaxRAMPercentage=(\\d+)"));
        long heap = memLimit * ramPercentage / 100;

        long worstCase = ImageIoReencoder.worstCaseHeapBytes(
                ImageIoReencoder.DEFAULT_MAX_PIXELS, ImageIoReencoder.DEFAULT_MAX_CONCURRENT);

        assertTrue(worstCase + UPLOAD_BUFFERS + SERVICE_RESERVE <= heap,
                "re-codificación %d MB + buffers %d MB + reserva %d MB > heap %d MB".formatted(
                        worstCase / MB, UPLOAD_BUFFERS / MB, SERVICE_RESERVE / MB, heap / MB));
    }

    private static Path composeFile() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("docker-compose.prod.yml");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se encuentra docker-compose.prod.yml");
    }

    /** El bloque del servicio hasta el siguiente servicio de primer nivel. */
    private static String section(String compose, String header) {
        int start = compose.indexOf(header);
        if (start < 0) {
            throw new IllegalStateException("docker-compose.prod.yml sin " + header.trim());
        }
        Matcher next = Pattern.compile("(?m)^  [a-z0-9-]+:\\s*$").matcher(compose);
        int end = next.find(start + header.length()) ? next.start() : compose.length();
        return compose.substring(start, end);
    }

    private static String firstMatch(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) {
            throw new IllegalStateException("docker-compose.prod.yml sin " + regex);
        }
        return m.group(1);
    }

    private static long megabytes(String value) {
        return Long.parseLong(value) * MB;
    }
}
