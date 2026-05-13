import { StyleSheet, Text, View } from "react-native";

import { colors } from "@/theme/colors";

type Accent = "amber" | "blue" | "green" | "slate" | "teal";

const accentColors: Record<Accent, string> = {
  amber: "#B7791F",
  blue: "#2563EB",
  green: "#15803D",
  slate: "#475569",
  teal: "#0F766E"
};

type MetricCardProps = {
  label: string;
  value: string;
  accent: Accent;
};

export function MetricCard({ label, value, accent }: MetricCardProps) {
  return (
    <View style={styles.card}>
      <View style={[styles.marker, { backgroundColor: accentColors[accent] }]} />
      <View style={styles.content}>
        <Text style={styles.label}>{label}</Text>
        <Text style={styles.value}>{value}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: "row",
    minHeight: 84,
    paddingHorizontal: 16,
    paddingVertical: 14
  },
  content: {
    gap: 4
  },
  label: {
    color: colors.muted,
    fontSize: 14,
    fontWeight: "600"
  },
  marker: {
    borderRadius: 4,
    height: 44,
    marginRight: 14,
    width: 6
  },
  value: {
    color: colors.text,
    fontSize: 24,
    fontWeight: "700"
  }
});
