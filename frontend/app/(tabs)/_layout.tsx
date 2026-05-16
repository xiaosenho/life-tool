import { Ionicons } from "@expo/vector-icons";
import { Tabs, useFocusEffect } from "expo-router";
import { useCallback, useEffect, useRef } from "react";

import { colors } from "@/theme/colors";
import { friendService } from "@/services/friendService";
import { useFriendBadgeStore } from "@/store/friendBadgeStore";

type IconName = keyof typeof Ionicons.glyphMap;
const BADGE_POLL_INTERVAL_MS = 3000;

const tabIcons: Record<string, IconName> = {
  index: "today-outline",
  focus: "timer-outline",
  records: "receipt-outline",
  ai: "sparkles-outline",
  friends: "people-outline",
  profile: "person-circle-outline"
};

export default function TabLayout() {
  const friendUnreadCount = useFriendBadgeStore((state) => state.totalUnreadCount);
  const syncFromConversations = useFriendBadgeStore((state) => state.syncFromConversations);
  const reset = useFriendBadgeStore((state) => state.reset);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const loadFriendUnread = useCallback(async () => {
    try {
      const response = await friendService.listConversations();
      if (!response.success || !response.data) {
        reset();
        return;
      }
      syncFromConversations(response.data);
    } catch {
      reset();
    }
  }, [reset, syncFromConversations]);

  useFocusEffect(
    useCallback(() => {
      void loadFriendUnread();
    }, [loadFriendUnread])
  );

  useEffect(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }

    void loadFriendUnread();

    pollingRef.current = setInterval(() => {
      void loadFriendUnread();
    }, BADGE_POLL_INTERVAL_MS);

    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
  }, [loadFriendUnread]);

  return (
    <Tabs
      backBehavior="history"
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: colors.accent,
        tabBarInactiveTintColor: colors.muted,
        tabBarStyle: {
          borderTopColor: colors.border,
          backgroundColor: colors.surface,
          height: 64,
          paddingBottom: 8,
          paddingTop: 6
        },
        tabBarIcon: ({ color, size }) => (
          <Ionicons name={tabIcons[route.name] ?? "ellipse-outline"} size={size} color={color} />
        ),
        tabBarBadge:
          route.name === "friends" && friendUnreadCount > 0
            ? friendUnreadCount > 99
              ? "99+"
              : friendUnreadCount
            : undefined,
        tabBarBadgeStyle:
          route.name === "friends"
            ? {
                backgroundColor: "#EF4444",
                color: "#FFF",
                fontSize: 11,
                fontWeight: "700",
                minWidth: 18,
                height: 18,
                lineHeight: 18
              }
            : undefined,
      })}
    >
      <Tabs.Screen name="index" options={{ title: "今日" }} />
      <Tabs.Screen name="focus" options={{ title: "专注" }} />
      <Tabs.Screen name="records" options={{ title: "记录" }} />
      <Tabs.Screen name="friends" options={{ title: "好友" }} />
      <Tabs.Screen name="profile" options={{ title: "我的" }} />
    </Tabs>
  );
}
