import { Ionicons } from "@expo/vector-icons";
import { Tabs } from "expo-router";

import { colors } from "@/theme/colors";

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
  return (
    <Tabs
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
        )
      })}
    >
      <Tabs.Screen name="index" options={{ title: "今日" }} />
      <Tabs.Screen name="focus" options={{ title: "专注" }} />
      <Tabs.Screen name="records" options={{ title: "记录" }} />
      <Tabs.Screen name="ai" options={{ title: "AI" }} />
      <Tabs.Screen name="friends" options={{ title: "好友" }} />
      <Tabs.Screen name="profile" options={{ title: "我的" }} />
    </Tabs>
  );
}
