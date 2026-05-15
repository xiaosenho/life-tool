package com.lifetool.friends.dto;

import jakarta.validation.Valid;

public record SendFriendMessageRequest(
        String content,
        String type,
        @Valid FriendMessageAttachmentRequest attachment
) {
}
