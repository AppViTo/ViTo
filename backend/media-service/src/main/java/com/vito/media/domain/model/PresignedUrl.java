package com.vito.media.domain.model;

import java.time.Instant;

/** Schema {@code PresignedUrl} de la Ola 3 (T-C09): URL de lectura de vida corta. */
public record PresignedUrl(String url, int expiresIn, Instant expiresAt) {
}
