package com.lifetool.friends.dto;

import jakarta.validation.constraints.NotBlank;

public record SendFriendMessageRequest(
        @NotBlank(message = "content is required")
        String content,
        String type
) {
}
