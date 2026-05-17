package com.lifetool.friends.realtime;

import com.lifetool.friends.dto.FriendConversationSummaryResponse;
import com.lifetool.friends.dto.FriendMessageResponse;

public record FriendMessageEventPayload(
        FriendMessageResponse message,
        FriendConversationSummaryResponse conversation
) {
}
