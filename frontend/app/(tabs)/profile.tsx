import { View } from "react-native";

import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";

export default function ProfileScreen() {
  return (
    <Screen title="我的">
      <View style={{ gap: 12 }}>
        <MetricCard label="账号" value="未登录" accent="slate" />
        <MetricCard label="隐私" value="默认私密" accent="green" />
      </View>
    </Screen>
  );
}
