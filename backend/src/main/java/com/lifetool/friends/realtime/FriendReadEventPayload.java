package com.lifetool.friends.realtime;

import com.lifetool.friends.dto.FriendConversationSummaryResponse;

public record FriendReadEventPayload(
        String friendUserId,
        int updated,
        FriendConversationSummaryResponse conversation
) {
}
