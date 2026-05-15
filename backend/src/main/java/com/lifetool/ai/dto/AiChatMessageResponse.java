package com.lifetool.ai.dto;

import java.time.Instant;
import java.util.List;

import com.lifetool.ai.AiChatMessage;

public record AiChatMessageResponse(
        String id,
        String messageId,
        String role,
        String content,
        String disclaimer,
        List<AiToolCallStatusResponse> toolCalls,
        boolean longTermMemorySaved,
        Instant createdAt
) {
    public static AiChatMessageResponse from(AiChatMessage message, String disclaimer,
                                             List<AiToolCallStatusResponse> toolCalls) {
        return from(message, disclaimer, toolCalls, false);
    }

    public static AiChatMessageResponse from(AiChatMessage message, String disclaimer,
                                             List<AiToolCallStatusResponse> toolCalls,
                                             boolean longTermMemorySaved) {
        return new AiChatMessageResponse(
                message.getId(),
                message.getId(),
                message.getRole(),
                message.getContent(),
                disclaimer,
                toolCalls,
                longTermMemorySaved,
                message.getCreatedAt());
    }
}
