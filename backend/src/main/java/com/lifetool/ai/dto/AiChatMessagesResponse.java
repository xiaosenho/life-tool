package com.lifetool.ai.dto;

import java.util.List;

public record AiChatMessagesResponse(
        List<AiChatMessageResponse> messages
) {
}
