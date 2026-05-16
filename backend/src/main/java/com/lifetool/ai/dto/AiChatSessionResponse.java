package com.lifetool.ai.dto;

import java.time.Instant;

import com.lifetool.ai.AiChatSession;

public record AiChatSessionResponse(
        String id,
        String title,
        boolean useLongTermMemory,
        Instant createdAt,
        Instant updatedAt
) {
    public static AiChatSessionResponse from(AiChatSession session) {
        return new AiChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.isUseLongTermMemory(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}
