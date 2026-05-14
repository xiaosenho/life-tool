package com.lifetool.friends;

import java.util.List;
import java.util.Optional;

public interface FriendStore {
    FriendRequest saveRequest(FriendRequest request);

    Optional<FriendRequest> findRequestById(String id);

    Optional<FriendRequest> findPendingRequest(String fromUserId, String toUserId);

    Optional<FriendRequest> findPendingRequestBetween(String userId1, String userId2);

    List<FriendRequest> findRequestsByUser(String userId);

    Friendship saveFriendship(Friendship friendship);

    List<Friendship> findFriendships(String userId);

    boolean areFriends(String userId1, String userId2);

    void removeFriendship(String userId, String friendUserId);
}
