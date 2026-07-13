import { Ionicons } from "@expo/vector-icons";
import { Tabs } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "@/theme/colors";
import { useFriendBadgeStore } from "@/store/friendBadgeStore";

type IconName = keyof typeof Ionicons.glyphMap;

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
  const insets = useSafeAreaInsets();
  const tabBarBottomInset = Math.max(insets.bottom, 8);

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
          height: 56 + tabBarBottomInset,
          paddingBottom: tabBarBottomInset,
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
