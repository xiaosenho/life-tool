import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";

export default function FocusScreen() {
  return (
    <Screen title="专注">
      <MetricCard label="当前计时" value="25:00" accent="teal" />
    </Screen>
  );
}
