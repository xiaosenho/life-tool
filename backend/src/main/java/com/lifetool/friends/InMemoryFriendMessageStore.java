package com.lifetool.friends;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryFriendMessageStore implements FriendMessageStore {

    private final CopyOnWriteArrayList<FriendMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public FriendMessage save(FriendMessage message) {
        messages.removeIf(existing -> existing.getId().equals(message.getId()));
        messages.add(message);
        return message;
    }

    @Override
    public List<FriendMessage> listConversation(String userId, String friendUserId) {
        return messages.stream()
                .filter(message -> isConversation(message, userId, friendUserId))
                .sorted(Comparator.comparing(FriendMessage::getCreatedAt))
                .toList();
    }

    @Override
    public List<FriendMessage> listByUser(String userId) {
        return messages.stream()
                .filter(message -> message.getFromUserId().equals(userId) || message.getToUserId().equals(userId))
                .sorted(Comparator.comparing(FriendMessage::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public int markConversationRead(String userId, String friendUserId) {
        int updated = 0;
        Instant now = Instant.now();
        for (FriendMessage message : messages) {
            if (message.getToUserId().equals(userId)
                    && message.getFromUserId().equals(friendUserId)
                    && !message.isRead()) {
                message.markRead(now);
                updated++;
            }
        }
        return updated;
    }

    private boolean isConversation(FriendMessage message, String userId, String friendUserId) {
        return (message.getFromUserId().equals(userId) && message.getToUserId().equals(friendUserId))
                || (message.getFromUserId().equals(friendUserId) && message.getToUserId().equals(userId));
    }
}
