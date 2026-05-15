package com.lifetool.friends.dto;

import java.time.Instant;

import com.lifetool.friends.FriendMessage;
import com.lifetool.friends.FriendMessageAttachment;

public record FriendMessageResponse(
        String id,
        String fromUserId,
        String toUserId,
        String type,
        String content,
        FriendMessageAttachment attachment,
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
                message.getAttachment(),
                message.getCreatedAt(),
                message.getReadAt());
    }
}
