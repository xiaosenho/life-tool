package com.lifetool.friends.dto;

import java.util.List;

public record FriendMessagePageResponse(
        List<FriendMessageResponse> messages,
        int limit,
        boolean hasMore
) {
}
