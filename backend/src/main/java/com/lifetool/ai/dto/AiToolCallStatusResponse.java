package com.lifetool.ai.dto;

import com.lifetool.ai.AiToolCall;

public record AiToolCallStatusResponse(
        String toolName,
        String status
) {
    public static AiToolCallStatusResponse from(AiToolCall toolCall) {
        return new AiToolCallStatusResponse(toolCall.getToolName(), toolCall.getStatus());
    }
}
