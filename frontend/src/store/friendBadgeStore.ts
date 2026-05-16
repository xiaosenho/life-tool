import { create } from "zustand";

import { FriendConversationSummary } from "@/services/friendService";

type ConversationUnreadMap = Record<string, number>;

interface FriendBadgeState {
  conversations: FriendConversationSummary[];
  conversationUnread: ConversationUnreadMap;
  totalUnreadCount: number;
  syncFromConversations: (conversations: FriendConversationSummary[]) => void;
  clearConversationUnread: (friendUserId: string) => void;
  upsertConversation: (conversation: FriendConversationSummary) => void;
  reset: () => void;
}

function computeTotalUnreadCount(conversationUnread: ConversationUnreadMap) {
  return Object.values(conversationUnread).reduce((sum, count) => sum + count, 0);
}

function sortConversations(conversations: FriendConversationSummary[]) {
  return [...conversations].sort(
    (left, right) => new Date(right.lastMessageAt).getTime() - new Date(left.lastMessageAt).getTime()
  );
}

export const useFriendBadgeStore = create<FriendBadgeState>((set) => ({
  conversations: [],
  conversationUnread: {},
  totalUnreadCount: 0,
  syncFromConversations: (conversations) => {
    const conversationUnread = conversations.reduce<ConversationUnreadMap>((result, item) => {
      result[item.friendUserId] = item.unreadCount || 0;
      return result;
    }, {});

    set({
      conversations,
      conversationUnread,
      totalUnreadCount: computeTotalUnreadCount(conversationUnread)
    });
  },
  clearConversationUnread: (friendUserId) =>
    set((state) => {
      if (!state.conversationUnread[friendUserId]) {
        return state;
      }

      const conversationUnread = {
        ...state.conversationUnread,
        [friendUserId]: 0
      };

      return {
        conversations: state.conversations.map((item) =>
          item.friendUserId === friendUserId ? { ...item, unreadCount: 0 } : item
        ),
        conversationUnread,
        totalUnreadCount: computeTotalUnreadCount(conversationUnread)
      };
    }),
  upsertConversation: (conversation) =>
    set((state) => {
      const existing = state.conversations.find((item) => item.friendUserId === conversation.friendUserId);
      const conversations = sortConversations(
        existing
          ? state.conversations.map((item) =>
              item.friendUserId === conversation.friendUserId ? { ...item, ...conversation } : item
            )
          : [conversation, ...state.conversations]
      );

      const nextUnreadCount = conversation.unreadCount ?? existing?.unreadCount ?? 0;
      const conversationUnread = {
        ...state.conversationUnread,
        [conversation.friendUserId]: nextUnreadCount
      };

      return {
        conversations,
        conversationUnread,
        totalUnreadCount: computeTotalUnreadCount(conversationUnread)
      };
    }),
  reset: () =>
    set({
      conversations: [],
      conversationUnread: {},
      totalUnreadCount: 0
    })
}));
