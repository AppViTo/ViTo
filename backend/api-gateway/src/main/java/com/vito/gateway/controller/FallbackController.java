package com.vito.gateway.controller;

import com.vito.gateway.error.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Destino de {@code fallbackUri: forward:/fallback/<servicio>} del CircuitBreaker de cada ruta.
 * El forward conserva el método original (POST, PUT, DELETE…): por eso acepta todos; con solo
 * GET, un POST en fallback respondía 405.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /** Nombre en es-ES de cada segmento de fallbackUri de application.yml. */
    private static final Map<String, String> SERVICE_NAMES = Map.ofEntries(
            Map.entry("auth", "autenticación"),
            Map.entry("social", "red social"),
            Map.entry("events", "eventos"),
            Map.entry("checkin", "check-in"),
            Map.entry("mosaic", "mosaico"),
            Map.entry("streaming", "streaming"),
            Map.entry("archive", "archivo"),
            Map.entry("chat", "chat"),
            Map.entry("media", "multimedia"),
            Map.entry("transcode", "procesamiento de video"),
            Map.entry("recommendations", "recomendaciones"),
            Map.entry("moderation", "moderación"));

    @RequestMapping("/{service}")
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable String service) {
        String name = SERVICE_NAMES.get(service);
        String message = name != null
                ? "El servicio de " + name + " no está disponible en este momento. Inténtalo de nuevo en unos segundos"
                : "El servicio no está disponible en este momento. Inténtalo de nuevo en unos segundos";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponses.body(HttpStatus.SERVICE_UNAVAILABLE, ErrorResponses.SERVICE_UNAVAILABLE, message));
    }
}
