package com.lifetool.friends;

public record FriendMessageAttachment(
        String assetId,
        String kind,
        String contentType,
        String url,
        Integer width,
        Integer height,
        Integer durationSeconds
) {
}
