package com.lifetool.friends;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

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
    public List<ConversationSummary> listConversationSummaries(String userId) {
        Map<String, List<FriendMessage>> grouped = messages.stream()
                .filter(message -> message.getFromUserId().equals(userId) || message.getToUserId().equals(userId))
                .collect(Collectors.groupingBy(message -> message.getFromUserId().equals(userId)
                        ? message.getToUserId()
                        : message.getFromUserId()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<FriendMessage> conversation = entry.getValue();
                    FriendMessage latest = conversation.stream()
                            .max(Comparator.comparing(FriendMessage::getCreatedAt))
                            .orElse(null);
                    if (latest == null) {
                        return null;
                    }
                    int unreadCount = (int) conversation.stream()
                            .filter(message -> message.getToUserId().equals(userId) && !message.isRead())
                            .count();
                    return new ConversationSummary(
                            entry.getKey(),
                            latest.getContent(),
                            latest.getType().name().toLowerCase(),
                            latest.getCreatedAt(),
                            unreadCount);
                })
                .filter(summary -> summary != null)
                .sorted((left, right) -> right.lastMessageAt().compareTo(left.lastMessageAt()))
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
