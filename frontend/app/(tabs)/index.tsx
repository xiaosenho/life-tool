import { View } from "react-native";
import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";
import { useAuthStore } from "@/store/authStore";

export default function TodayScreen() {
  const { isAuthenticated } = useAuthStore();

  return (
    <Screen title="今日">
      <View style={{ gap: 12 }}>
        <MetricCard label="专注" value="0 分钟" accent="teal" />
        <MetricCard label="习惯" value="0 / 0" accent="blue" />
        <MetricCard
          label="同步"
          value={isAuthenticated ? "已就绪" : "未登录"}
          accent="slate"
        />
      </View>
    </Screen>
  );
}
