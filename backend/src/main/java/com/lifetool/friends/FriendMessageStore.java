package com.lifetool.friends;

import java.util.List;

public interface FriendMessageStore {
    record ConversationSummary(
            String friendUserId,
            String lastMessage,
            String lastMessageType,
            java.time.Instant lastMessageAt,
            int unreadCount
    ) {
    }

    FriendMessage save(FriendMessage message);

    List<FriendMessage> listConversation(String userId, String friendUserId);

    List<FriendMessage> listByUser(String userId);

    List<ConversationSummary> listConversationSummaries(String userId);

    ConversationSummary getConversationSummary(String userId, String friendUserId);

    int markConversationRead(String userId, String friendUserId);
}
