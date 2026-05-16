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

export type FriendMessageType = "text" | "cheer" | "celebrate" | "hug" | "coffee" | "poke";
export type FriendMessageMediaType = FriendMessageType | "image" | "audio";

export const FRIEND_MESSAGE_TYPE_LABELS: Record<FriendMessageMediaType, string> = {
  text: "消息",
  cheer: "加油",
  celebrate: "庆祝",
  hug: "抱抱",
  coffee: "咖啡",
  poke: "提醒",
  image: "图片",
  audio: "语音"
};

export interface FriendMessageAttachment {
  assetId: string;
  kind: "image" | "audio";
  contentType: string;
  url: string;
  width?: number;
  height?: number;
  durationSeconds?: number;
}

export interface FriendConversationSummary {
  friendUserId: string;
  friendDisplayName: string;
  friendEmail: string;
  lastMessage: string;
  lastMessageType: FriendMessageMediaType;
  lastMessageAt: string;
  unreadCount: number;
}

export interface FriendMessage {
  id: string;
  fromUserId: string;
  toUserId: string;
  type: FriendMessageMediaType;
  content: string;
  attachment?: FriendMessageAttachment | null;
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

  sendMessage(
    friendUserId: string,
    content: string,
    type: FriendMessageMediaType = "text",
    attachment?: { assetId: string; width?: number; height?: number; durationSeconds?: number }
  ) {
    return apiClient.post<FriendMessage>(`/friends/messages/${friendUserId}`, { content, type, attachment });
  },

  markConversationRead(friendUserId: string) {
    return apiClient.post<{ updated: number }>(`/friends/messages/${friendUserId}/read`);
  }
};
