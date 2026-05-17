import { apiClient } from "./apiClient";
import { API_BASE_URL } from "@/constants/config";
import { useAuthStore } from "@/store/authStore";

export interface FriendInfo {
  userId: string;
  email: string;
  displayName: string;
  avatarUrl?: string | null;
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
  friendAvatarUrl?: string | null;
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

export interface FriendMessagePage {
  messages: FriendMessage[];
  limit: number;
  hasMore: boolean;
}

export type FriendRealtimeEventType =
  | "friend_message_created"
  | "friend_request_created"
  | "friend_request_updated"
  | "friend_conversation_read";

export interface FriendRealtimeEvent<T = unknown> {
  id: string;
  type: FriendRealtimeEventType;
  userId: string;
  createdAt: string;
  payload: T;
}

export interface FriendMessageEventPayload {
  message: FriendMessage;
  conversation: FriendConversationSummary | null;
}

export interface FriendRequestEventPayload {
  request: FriendRequest;
}

export interface FriendReadEventPayload {
  friendUserId: string;
  updated: number;
  conversation: FriendConversationSummary | null;
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

  listMessages(friendUserId: string, options?: { limit?: number; beforeCreatedAt?: string; beforeId?: string }) {
    const params = new URLSearchParams();
    if (options?.limit) {
      params.set("limit", String(options.limit));
    }
    if (options?.beforeCreatedAt) {
      params.set("beforeCreatedAt", options.beforeCreatedAt);
    }
    if (options?.beforeId) {
      params.set("beforeId", options.beforeId);
    }
    const suffix = params.toString() ? `?${params.toString()}` : "";
    return apiClient.get<FriendMessagePage>(`/friends/messages/${friendUserId}${suffix}`);
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
  },

  createEventStreamUrl() {
    const token = useAuthStore.getState().token;
    if (!token) {
      return null;
    }
    const url = new URL(`${API_BASE_URL}/friends/events/stream`);
    url.searchParams.set("access_token", token);
    return url.toString();
  }
};
