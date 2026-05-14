import { apiClient } from "./apiClient";

export interface FriendInfo {
  userId: string;
  email: string;
  displayName: string;
}

export type FriendRequestStatus = "PENDING" | "ACCEPTED" | "REJECTED";

export interface FriendRequest {
  id: string;
  fromUserId: string;
  toUserId: string;
  status: FriendRequestStatus;
  createdAt: string;
  updatedAt: string;
}

export const friendService = {
  listFriends() {
    return apiClient.get<FriendInfo[]>("/friends");
  },

  sendRequest(email: string) {
    return apiClient.post<FriendRequest>("/friends/requests", { email });
  },

  listRequests() {
    return apiClient.get<FriendRequest[]>("/friends/requests");
  },

  acceptRequest(id: string) {
    return apiClient.patch<FriendRequest>(`/friends/requests/${id}`, { action: "accept" });
  },

  rejectRequest(id: string) {
    return apiClient.patch<FriendRequest>(`/friends/requests/${id}`, { action: "reject" });
  },

  removeFriend(friendUserId: string) {
    return apiClient.delete<void>(`/friends/${friendUserId}`);
  }
};
