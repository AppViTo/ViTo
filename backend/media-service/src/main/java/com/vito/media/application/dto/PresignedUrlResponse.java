package com.vito.media.application.dto;

import com.vito.media.domain.model.Media;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record PresignedUrlResponse(
        String url,
        long expirationMinutes
) {}
