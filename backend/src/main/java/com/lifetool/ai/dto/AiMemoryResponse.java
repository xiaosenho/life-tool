package com.lifetool.ai.dto;

import java.time.Instant;

import com.lifetool.ai.AiMemoryItem;

public record AiMemoryResponse(
        String id,
        String type,
        String content,
        String source,
        Instant createdAt
) {
    public static AiMemoryResponse from(AiMemoryItem memory) {
        return new AiMemoryResponse(
                memory.getId(),
                memory.getType(),
                memory.getContent(),
                memory.getSource(),
                memory.getCreatedAt());
    }
}
