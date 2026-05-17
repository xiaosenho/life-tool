import { create } from "zustand";

import { FriendConversationSummary, FriendMessage } from "@/services/friendService";

type ConversationUnreadMap = Record<string, number>;

interface FriendBadgeState {
  conversations: FriendConversationSummary[];
  conversationUnread: ConversationUnreadMap;
  totalUnreadCount: number;
  syncFromConversations: (conversations: FriendConversationSummary[]) => void;
  replaceConversations: (conversations: FriendConversationSummary[]) => void;
  clearConversationUnread: (friendUserId: string) => void;
  upsertConversation: (conversation: FriendConversationSummary) => void;
  applyIncomingMessage: (message: FriendMessage, conversation: FriendConversationSummary | null) => void;
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

function mergeConversationAvatarUrl(
  existing: FriendConversationSummary | undefined,
  incoming: FriendConversationSummary
) {
  if (
    existing?.friendAvatarAssetId &&
    incoming.friendAvatarAssetId &&
    existing.friendAvatarAssetId === incoming.friendAvatarAssetId &&
    existing.friendAvatarUrl
  ) {
    return existing.friendAvatarUrl;
  }
  return incoming.friendAvatarUrl ?? existing?.friendAvatarUrl ?? null;
}

export const useFriendBadgeStore = create<FriendBadgeState>((set) => ({
  conversations: [],
  conversationUnread: {},
  totalUnreadCount: 0,
  syncFromConversations: (conversations) => {
    set((state) => {
      const existingByFriendId = new Map(state.conversations.map((item) => [item.friendUserId, item]));
      const mergedConversations = conversations.map((item) => {
        const existing = existingByFriendId.get(item.friendUserId);
        return {
          ...item,
          friendAvatarUrl: mergeConversationAvatarUrl(existing, item)
        };
      });
      const conversationUnread = mergedConversations.reduce<ConversationUnreadMap>((result, item) => {
        result[item.friendUserId] = item.unreadCount || 0;
        return result;
      }, {});

      return {
        conversations: sortConversations(mergedConversations),
        conversationUnread,
        totalUnreadCount: computeTotalUnreadCount(conversationUnread)
      };
    });
  },
  replaceConversations: (conversations) => {
    const conversationUnread = conversations.reduce<ConversationUnreadMap>((result, item) => {
      result[item.friendUserId] = item.unreadCount || 0;
      return result;
    }, {});
    set({
      conversations: sortConversations(conversations),
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
      const nextConversation = {
        ...(existing ?? {}),
        ...conversation,
        friendAvatarUrl: mergeConversationAvatarUrl(existing, conversation)
      } as FriendConversationSummary;
      const conversations = sortConversations(
        existing
          ? state.conversations.map((item) =>
              item.friendUserId === conversation.friendUserId ? nextConversation : item
            )
          : [nextConversation, ...state.conversations]
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
  applyIncomingMessage: (message, conversation) =>
    set((state) => {
      const friendUserId = message.fromUserId;
      const existing = state.conversations.find((item) => item.friendUserId === friendUserId);
      const unreadCount = Math.max(0, (state.conversationUnread[friendUserId] ?? existing?.unreadCount ?? 0) + 1);
      const nextConversationBase: FriendConversationSummary = {
        friendUserId,
        friendDisplayName: conversation?.friendDisplayName ?? existing?.friendDisplayName ?? "",
        friendEmail: conversation?.friendEmail ?? existing?.friendEmail ?? "",
        friendAvatarAssetId: conversation?.friendAvatarAssetId ?? existing?.friendAvatarAssetId ?? null,
        friendAvatarUrl: conversation?.friendAvatarUrl ?? existing?.friendAvatarUrl ?? null,
        lastMessage: conversation?.lastMessage ?? message.content,
        lastMessageType: conversation?.lastMessageType ?? message.type,
        lastMessageAt: conversation?.lastMessageAt ?? message.createdAt,
        unreadCount
      };
      const nextConversation: FriendConversationSummary = {
        ...nextConversationBase,
        friendAvatarUrl: mergeConversationAvatarUrl(existing, nextConversationBase)
      };
      const conversations = sortConversations(
        existing
          ? state.conversations.map((item) => (item.friendUserId === friendUserId ? nextConversation : item))
          : [nextConversation, ...state.conversations]
      );
      const conversationUnread = {
        ...state.conversationUnread,
        [friendUserId]: unreadCount
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
