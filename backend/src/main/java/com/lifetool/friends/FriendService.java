package com.lifetool.friends;

import java.util.List;

import org.springframework.stereotype.Service;

import com.lifetool.users.User;
import com.lifetool.users.UserRepository;

@Service
public class FriendService {

    private final FriendStore store;
    private final UserRepository userRepo;

    public FriendService(FriendStore store, UserRepository userRepo) {
        this.store = store;
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
}
