package com.lifetool.ai;

public record AiChatAttachment(
        String assetId,
        String kind,
        String contentType,
        String url,
        Integer width,
        Integer height,
        Integer durationSeconds
) {
}
