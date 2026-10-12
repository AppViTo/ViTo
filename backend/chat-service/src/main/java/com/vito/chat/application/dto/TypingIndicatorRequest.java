package com.vito.chat.application.dto;

import com.vito.chat.domain.model.ChatMessage;
import com.vito.chat.domain.model.ChatRoom;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record TypingIndicatorRequest(
        @NotBlank(message = "Room ID is required")
        String roomId,
        
        boolean isTyping
) {}
