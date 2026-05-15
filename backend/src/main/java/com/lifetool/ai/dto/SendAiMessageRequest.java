package com.lifetool.ai.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendAiMessageRequest(
        @Size(max = 2000) String content,
        List<String> enabledTools,
        @Valid AiChatAttachmentRequest attachment
) {
}
