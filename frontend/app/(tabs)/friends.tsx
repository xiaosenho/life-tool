import { View } from "react-native";

import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";

export default function FriendsScreen() {
  return (
    <Screen title="好友">
      <View style={{ gap: 12 }}>
        <MetricCard label="今日排行" value="未加入" accent="blue" />
        <MetricCard label="本周专注" value="0 分钟" accent="teal" />
      </View>
    </Screen>
  );
}
