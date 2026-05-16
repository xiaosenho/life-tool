package com.lifetool.friends.dto;

import java.time.Instant;

public record FriendConversationSummaryResponse(
        String friendUserId,
        String friendDisplayName,
        String friendEmail,
        String lastMessage,
        String lastMessageType,
        Instant lastMessageAt,
        int unreadCount
) {
}
