package com.lifetool.ai.dto;

import java.time.Instant;
import java.util.List;

import com.lifetool.ai.AiChatAttachment;
import com.lifetool.ai.AiChatMessage;

public record AiChatMessageResponse(
        String id,
        String messageId,
        String role,
        String content,
        AiChatAttachment attachment,
        String disclaimer,
        List<AiToolCallStatusResponse> toolCalls,
        boolean longTermMemorySaved,
        Instant createdAt
) {
    public static AiChatMessageResponse from(AiChatMessage message, String disclaimer,
                                             List<AiToolCallStatusResponse> toolCalls) {
        return from(message, message.getAttachment(), disclaimer, toolCalls, false);
    }

    public static AiChatMessageResponse from(AiChatMessage message, String disclaimer,
                                             List<AiToolCallStatusResponse> toolCalls,
                                             boolean longTermMemorySaved) {
        return from(message, message.getAttachment(), disclaimer, toolCalls, longTermMemorySaved);
    }

    public static AiChatMessageResponse from(AiChatMessage message,
                                             AiChatAttachment attachment,
                                             String disclaimer,
                                             List<AiToolCallStatusResponse> toolCalls,
                                             boolean longTermMemorySaved) {
        return new AiChatMessageResponse(
                message.getId(),
                message.getId(),
                message.getRole(),
                message.getContent(),
                attachment,
                disclaimer,
                toolCalls,
                longTermMemorySaved,
                message.getCreatedAt());
    }
}
