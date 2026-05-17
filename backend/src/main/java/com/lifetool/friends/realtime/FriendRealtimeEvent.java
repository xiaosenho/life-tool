package com.lifetool.friends.realtime;

import java.time.Instant;

public record FriendRealtimeEvent(
        String id,
        FriendEventType type,
        String userId,
        Instant createdAt,
        Object payload
) {
}
