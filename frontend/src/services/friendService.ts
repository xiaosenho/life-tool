import { apiClient } from "./apiClient";
import { useAuthStore } from "@/store/authStore";

export interface FriendInfo {
  userId: string;
  email: string;
  displayName: string;
}

export interface FriendRequest {
  id: string;
  fromUserId: string;
  toUserId: string;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
  createdAt: string;
  updatedAt: string;
}

const MOCK_ENABLED = true;

let mockFriends: FriendInfo[] = [
  { userId: "friend1", email: "alice@example.com", displayName: "Alice" },
  { userId: "friend2", email: "bob@example.com", displayName: "Bob" },
];

let mockRequests: FriendRequest[] = [];

export const friendService = {
  async listFriends(): Promise<FriendInfo[]> {
    if (MOCK_ENABLED) {
      return [...mockFriends];
    }
    const resp = await apiClient.get<FriendInfo[]>("/friends");
    return resp.data ?? [];
  },

  async sendRequest(email: string): Promise<FriendRequest> {
    if (MOCK_ENABLED) {
      const currentUser = useAuthStore.getState().user;
      const request: FriendRequest = {
        id: `req_${Date.now()}`,
        fromUserId: currentUser?.id ?? "me",
        toUserId: email,
        status: "PENDING",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      mockRequests.push(request);
      return request;
    }
    const resp = await apiClient.post<FriendRequest>("/friends/requests", { email });
    if (!resp.success || !resp.data) {
      throw new Error(resp.error?.message ?? "发送好友请求失败");
    }
    return resp.data;
  },

  async listRequests(): Promise<FriendRequest[]> {
    if (MOCK_ENABLED) {
      return [...mockRequests];
    }
    const resp = await apiClient.get<FriendRequest[]>("/friends/requests");
    return resp.data ?? [];
  },

  async handleRequest(requestId: string, action: "accept" | "reject"): Promise<FriendRequest> {
    if (MOCK_ENABLED) {
      const idx = mockRequests.findIndex((r) => r.id === requestId);
      if (idx === -1) throw new Error("请求不存在");
      const updated = {
        ...mockRequests[idx],
        status: action === "accept" ? "ACCEPTED" as const : "REJECTED" as const,
        updatedAt: new Date().toISOString(),
      };
      mockRequests[idx] = updated;
      if (action === "accept") {
        mockFriends.push({
          userId: updated.fromUserId,
          email: updated.fromUserId,
          displayName: "New Friend",
        });
      }
      return updated;
    }
    const resp = await apiClient.patch<FriendRequest>(`/friends/requests/${requestId}`, { action });
    if (!resp.success || !resp.data) {
      throw new Error(resp.error?.message ?? "操作失败");
    }
    return resp.data;
  },

  async removeFriend(friendUserId: string): Promise<void> {
    if (MOCK_ENABLED) {
      mockFriends = mockFriends.filter((f) => f.userId !== friendUserId);
      return;
    }
    await apiClient.delete(`/friends/${friendUserId}`);
  },
};
