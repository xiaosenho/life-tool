package com.lifetool.friends;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.lifetool.friends.dto.FriendConversationSummaryResponse;
import com.lifetool.users.User;
import com.lifetool.users.UserRepository;

@Service
public class FriendService {

    private final FriendStore store;
    private final FriendMessageStore messageStore;
    private final UserRepository userRepo;

    public FriendService(FriendStore store, FriendMessageStore messageStore, UserRepository userRepo) {
        this.store = store;
        this.messageStore = messageStore;
        this.userRepo = userRepo;
    }

    public FriendRequest sendRequest(String fromUserId, String targetEmail) {
        User target = userRepo.findByEmail(targetEmail)
                .orElseThrow(() -> new FriendException("NOT_FOUND", "User not found"));

        if (target.getId().equals(fromUserId)) {
            throw new FriendException("VALIDATION_ERROR", "Cannot add yourself as a friend");
        }
        if (store.areFriends(fromUserId, target.getId())) {
            throw new FriendException("CONFLICT", "Already friends");
        }
        if (store.findPendingRequestBetween(fromUserId, target.getId()).isPresent()) {
            throw new FriendException("CONFLICT", "Friend request already pending");
        }

        return store.saveRequest(new FriendRequest(fromUserId, target.getId()));
    }

    public List<FriendRequest> listRequests(String userId) {
        return store.findRequestsByUser(userId);
    }

    public FriendRequest handleRequest(String userId, String requestId, String action) {
        FriendRequest request = store.findRequestById(requestId)
                .orElseThrow(() -> new FriendException("NOT_FOUND", "Request not found"));

        if (!request.getToUserId().equals(userId)) {
            throw new FriendException("FORBIDDEN", "Only the recipient can respond to this request");
        }
        if (request.getStatus() != FriendRequest.Status.PENDING) {
            throw new FriendException("CONFLICT", "Request already handled");
        }

        if ("accept".equals(action)) {
            request.setStatus(FriendRequest.Status.ACCEPTED);
            store.saveFriendship(new Friendship(request.getFromUserId(), request.getToUserId()));
        } else if ("reject".equals(action)) {
            request.setStatus(FriendRequest.Status.REJECTED);
            store.saveRequest(request);
        } else {
            throw new FriendException("VALIDATION_ERROR", "Invalid action, must be 'accept' or 'reject'");
        }

        return request;
    }

    public record FriendInfo(String userId, String email, String displayName) {}

    public List<FriendInfo> listFriends(String userId) {
        return store.findFriendships(userId).stream()
                .map(f -> {
                    String friendId = f.getUserId().equals(userId) ? f.getFriendUserId() : f.getUserId();
                    User friend = userRepo.findById(friendId).orElse(null);
                    if (friend == null) return null;
                    return new FriendInfo(friend.getId(), friend.getEmail(), friend.getDisplayName());
                })
                .filter(f -> f != null)
                .toList();
    }

    public void removeFriend(String userId, String friendUserId) {
        if (!store.areFriends(userId, friendUserId)) {
            throw new FriendException("NOT_FOUND", "Not friends");
        }
        store.removeFriendship(userId, friendUserId);
    }

    public FriendMessage sendMessage(String userId, String friendUserId, String content, String type) {
        ensureFriends(userId, friendUserId);
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            throw new FriendException("VALIDATION_ERROR", "content is required");
        }
        FriendMessage.MessageType messageType = "cheer".equalsIgnoreCase(type)
                ? FriendMessage.MessageType.CHEER
                : FriendMessage.MessageType.TEXT;
        return messageStore.save(new FriendMessage(userId, friendUserId, messageType, normalized));
    }

    public List<FriendMessage> listConversation(String userId, String friendUserId) {
        ensureFriends(userId, friendUserId);
        return messageStore.listConversation(userId, friendUserId);
    }

    public int markConversationRead(String userId, String friendUserId) {
        ensureFriends(userId, friendUserId);
        return messageStore.markConversationRead(userId, friendUserId);
    }

    public List<FriendConversationSummaryResponse> listConversations(String userId) {
        Map<String, FriendInfo> friends = listFriends(userId).stream()
                .collect(Collectors.toMap(FriendInfo::userId, info -> info));
        return messageStore.listByUser(userId).stream()
                .collect(Collectors.groupingBy(message -> conversationFriendId(userId, message)))
                .entrySet().stream()
                .map(entry -> {
                    String friendId = entry.getKey();
                    FriendInfo info = friends.get(friendId);
                    if (info == null) {
                        return null;
                    }
                    List<FriendMessage> messages = entry.getValue();
                    FriendMessage latest = messages.stream()
                            .max(java.util.Comparator.comparing(FriendMessage::getCreatedAt))
                            .orElse(null);
                    int unreadCount = (int) messages.stream()
                            .filter(message -> message.getToUserId().equals(userId) && !message.isRead())
                            .count();
                    return latest == null ? null : new FriendConversationSummaryResponse(
                            friendId,
                            info.displayName(),
                            info.email(),
                            latest.getContent(),
                            latest.getType().name().toLowerCase(),
                            latest.getCreatedAt(),
                            unreadCount);
                })
                .filter(item -> item != null)
                .sorted((left, right) -> right.lastMessageAt().compareTo(left.lastMessageAt()))
                .toList();
    }

    private void ensureFriends(String userId, String friendUserId) {
        if (!store.areFriends(userId, friendUserId)) {
            throw new FriendException("FORBIDDEN", "Only friends can interact");
        }
    }

    private String conversationFriendId(String userId, FriendMessage message) {
        return message.getFromUserId().equals(userId) ? message.getToUserId() : message.getFromUserId();
    }
}
