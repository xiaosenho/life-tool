package com.lifetool.friends.realtime;

import com.lifetool.friends.FriendRequest;

public record FriendRequestEventPayload(
        FriendRequest request
) {
}
