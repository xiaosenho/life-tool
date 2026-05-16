package com.lifetool.friends.dto;

public record FriendMessageAttachmentRequest(
        String assetId,
        Integer width,
        Integer height,
        Integer durationSeconds
) {
}
