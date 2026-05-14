package com.lifetool.friends;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryFriendStore implements FriendStore {

    private final Map<String, FriendRequest> requestsById = new ConcurrentHashMap<>();
    private final Map<String, Friendship> friendshipsById = new ConcurrentHashMap<>();

    @Override
    public FriendRequest saveRequest(FriendRequest request) {
        requestsById.put(request.getId(), request);
        return request;
    }

    @Override
    public Optional<FriendRequest> findRequestById(String id) {
        return Optional.ofNullable(requestsById.get(id));
    }

    @Override
    public Optional<FriendRequest> findPendingRequest(String fromUserId, String toUserId) {
        return requestsById.values().stream()
                .filter(r -> r.getStatus() == FriendRequest.Status.PENDING)
                .filter(r -> r.getFromUserId().equals(fromUserId) && r.getToUserId().equals(toUserId))
                .findFirst();
    }

    @Override
    public Optional<FriendRequest> findPendingRequestBetween(String userId1, String userId2) {
        return requestsById.values().stream()
                .filter(r -> r.getStatus() == FriendRequest.Status.PENDING)
                .filter(r -> (r.getFromUserId().equals(userId1) && r.getToUserId().equals(userId2))
                        || (r.getFromUserId().equals(userId2) && r.getToUserId().equals(userId1)))
                .findFirst();
    }

    @Override
    public List<FriendRequest> findRequestsByUser(String userId) {
        return requestsById.values().stream()
                .filter(r -> r.getStatus() == FriendRequest.Status.PENDING)
                .filter(r -> r.getFromUserId().equals(userId) || r.getToUserId().equals(userId))
                .toList();
    }

    @Override
    public Friendship saveFriendship(Friendship friendship) {
        friendshipsById.put(friendship.getId(), friendship);
        return friendship;
    }

    @Override
    public List<Friendship> findFriendships(String userId) {
        return friendshipsById.values().stream()
                .filter(f -> f.getUserId().equals(userId) || f.getFriendUserId().equals(userId))
                .toList();
    }

    @Override
    public boolean areFriends(String userId1, String userId2) {
        return friendshipsById.values().stream()
                .anyMatch(f -> (f.getUserId().equals(userId1) && f.getFriendUserId().equals(userId2))
                        || (f.getUserId().equals(userId2) && f.getFriendUserId().equals(userId1)));
    }

    @Override
    public void removeFriendship(String userId, String friendUserId) {
        friendshipsById.values().removeIf(f ->
                (f.getUserId().equals(userId) && f.getFriendUserId().equals(friendUserId))
                        || (f.getUserId().equals(friendUserId) && f.getFriendUserId().equals(userId)));
    }
}
