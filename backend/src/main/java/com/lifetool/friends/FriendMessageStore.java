package com.lifetool.friends;

import java.util.List;

public interface FriendMessageStore {
    FriendMessage save(FriendMessage message);

    List<FriendMessage> listConversation(String userId, String friendUserId);

    List<FriendMessage> listByUser(String userId);

    int markConversationRead(String userId, String friendUserId);
}
