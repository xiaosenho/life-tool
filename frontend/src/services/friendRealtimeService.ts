import EventSource from "react-native-sse";

import {
  FriendConversationSummary,
  FriendMessage,
  FriendMessageEventPayload,
  FriendReadEventPayload,
  FriendRealtimeEvent,
  FriendRequest,
  FriendRequestEventPayload,
  friendService
} from "./friendService";
import { useAuthStore } from "@/store/authStore";

type Listener = {
  onMessage?: (message: FriendMessage, conversation: FriendConversationSummary | null) => void;
  onRequest?: (request: FriendRequest) => void;
  onRequestUpdated?: (request: FriendRequest) => void;
  onConversationRead?: (payload: FriendReadEventPayload) => void;
  onOpen?: () => void;
  onError?: (message: string) => void;
};

class FriendRealtimeService {
  private eventSource: EventSource<string> | null = null;
  private listeners = new Set<Listener>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  subscribe(listener: Listener) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  connect() {
    const token = useAuthStore.getState().token;
    const url = friendService.createEventStreamUrl();
    if (!token || !url || this.eventSource) {
      return;
    }

    this.eventSource = new EventSource(url, {
      headers: {
        Authorization: `Bearer ${token}`
      },
      pollingInterval: 0
    });

    this.eventSource.addEventListener("open", () => {
      this.listeners.forEach((listener) => listener.onOpen?.());
    });

    this.eventSource.addEventListener("friend_message_created", (event) => {
      const data = this.parseEvent<FriendMessageEventPayload>(event.data);
      if (!data) return;
      this.listeners.forEach((listener) => listener.onMessage?.(data.message, data.conversation));
    });

    this.eventSource.addEventListener("friend_request_created", (event) => {
      const data = this.parseEvent<FriendRequestEventPayload>(event.data);
      if (!data) return;
      this.listeners.forEach((listener) => listener.onRequest?.(data.request));
    });

    this.eventSource.addEventListener("friend_request_updated", (event) => {
      const data = this.parseEvent<FriendRequestEventPayload>(event.data);
      if (!data) return;
      this.listeners.forEach((listener) => listener.onRequestUpdated?.(data.request));
    });

    this.eventSource.addEventListener("friend_conversation_read", (event) => {
      const data = this.parseEvent<FriendReadEventPayload>(event.data);
      if (!data) return;
      this.listeners.forEach((listener) => listener.onConversationRead?.(data));
    });

    this.eventSource.addEventListener("error", (event) => {
      const maybeMessage = (event as { message?: string }).message;
      const message = typeof maybeMessage === "string" ? maybeMessage : "好友实时连接中断";
      this.listeners.forEach((listener) => listener.onError?.(message));
      this.disconnect();
      this.scheduleReconnect();
    });
  }

  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  private scheduleReconnect() {
    if (this.reconnectTimer || !useAuthStore.getState().token) {
      return;
    }
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, 3000);
  }

  private parseEvent<T>(raw: string | null | undefined): T | null {
    if (!raw) {
      return null;
    }
    try {
      const event = JSON.parse(raw) as FriendRealtimeEvent<T>;
      return event.payload;
    } catch {
      return null;
    }
  }
}

export const friendRealtimeService = new FriendRealtimeService();
