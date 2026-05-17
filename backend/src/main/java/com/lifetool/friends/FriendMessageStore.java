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

    record ConversationPage(
            List<FriendMessage> messages,
            boolean hasMore
    ) {
    }

    FriendMessage save(FriendMessage message);

    ConversationPage listConversation(String userId, String friendUserId, int limit, java.time.Instant beforeCreatedAt, String beforeId);

    List<FriendMessage> listByUser(String userId);

    List<ConversationSummary> listConversationSummaries(String userId);

    ConversationSummary getConversationSummary(String userId, String friendUserId);

    int markConversationRead(String userId, String friendUserId);
}
