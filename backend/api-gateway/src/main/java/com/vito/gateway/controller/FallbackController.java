package com.vito.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/{service}")
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Service temporarily unavailable",
                        "service", service,
                        "timestamp", LocalDateTime.now().toString(),
                        "message", "Please try again later"
                ));
    }

    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Auth service temporarily unavailable",
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    @GetMapping("/social")
    public ResponseEntity<Map<String, Object>> socialFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Social service temporarily unavailable",
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> eventsFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Events service temporarily unavailable",
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chatFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Chat service temporarily unavailable",
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    @GetMapping("/streaming")
    public ResponseEntity<Map<String, Object>> streamingFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Streaming service temporarily unavailable",
                        "timestamp", LocalDateTime.now().toString()
                ));
    }
}
