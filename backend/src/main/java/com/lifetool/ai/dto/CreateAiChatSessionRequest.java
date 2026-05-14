package com.lifetool.ai.dto;

public record CreateAiChatSessionRequest(
        String title,
        Boolean useLongTermMemory
) {
}
