package com.vito.media.application.dto;

import com.vito.media.domain.model.Media;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record UploadMediaRequest(
        @NotBlank(message = "Event ID is required")
        String eventId
) {}
