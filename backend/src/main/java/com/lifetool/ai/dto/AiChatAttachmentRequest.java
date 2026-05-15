package com.lifetool.ai.dto;

public record AiChatAttachmentRequest(
        String assetId,
        Integer width,
        Integer height,
        Integer durationSeconds
) {
}
