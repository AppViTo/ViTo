package com.vito.media.application.dto;

import com.vito.media.domain.model.PresignedUrl;

/** Schema {@code PresignedUrl} de openapi.yaml (T-C09): {@code expiresAt} en ISO-8601 UTC. */
public record PresignedUrlResponse(String url, int expiresIn, String expiresAt) {

    public static PresignedUrlResponse from(PresignedUrl presigned) {
        return new PresignedUrlResponse(presigned.url(), presigned.expiresIn(), presigned.expiresAt().toString());
    }
}
