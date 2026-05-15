package com.lifetool.friends.dto;

import java.time.Instant;

import com.lifetool.friends.FriendMessage;

public record FriendMessageResponse(
        String id,
        String fromUserId,
        String toUserId,
        String type,
        String content,
        Instant createdAt,
        Instant readAt
) {
    public static FriendMessageResponse from(FriendMessage message) {
        return new FriendMessageResponse(
                message.getId(),
                message.getFromUserId(),
                message.getToUserId(),
                message.getType().name().toLowerCase(),
                message.getContent(),
                message.getCreatedAt(),
                message.getReadAt());
    }
}
