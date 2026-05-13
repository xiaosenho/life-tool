import { View } from "react-native";

import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";

export default function RecordsScreen() {
  return (
    <Screen title="记录">
      <View style={{ gap: 12 }}>
        <MetricCard label="饮食" value="0 千卡" accent="green" />
        <MetricCard label="记账" value="¥0.00" accent="amber" />
        <MetricCard label="事件" value="0 条" accent="blue" />
      </View>
    </Screen>
  );
}
