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

export interface FriendConversationSummary {
  friendUserId: string;
  friendDisplayName: string;
  friendEmail: string;
  lastMessage: string;
  lastMessageType: "text" | "cheer";
  lastMessageAt: string;
  unreadCount: number;
}

export interface FriendMessage {
  id: string;
  fromUserId: string;
  toUserId: string;
  type: "text" | "cheer";
  content: string;
  createdAt: string;
  readAt?: string | null;
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
  },

  listConversations() {
    return apiClient.get<FriendConversationSummary[]>("/friends/messages");
  },

  listMessages(friendUserId: string) {
    return apiClient.get<FriendMessage[]>(`/friends/messages/${friendUserId}`);
  },

  sendMessage(friendUserId: string, content: string, type: "text" | "cheer" = "text") {
    return apiClient.post<FriendMessage>(`/friends/messages/${friendUserId}`, { content, type });
  },

  markConversationRead(friendUserId: string) {
    return apiClient.post<{ updated: number }>(`/friends/messages/${friendUserId}/read`);
  }
};
