import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { useRouter } from "expo-router";
import { MaterialCommunityIcons } from "@expo/vector-icons";

import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";
import { colors } from "@/theme/colors";

export default function RecordsScreen() {
  const router = useRouter();

  return (
    <Screen title="记录">
      <View style={{ gap: 12 }}>
        <MetricCard label="饮食" value="0 千卡" accent="green" />

        <TouchableOpacity
          style={styles.uploadCard}
          onPress={() => router.push("/meal-upload")}
        >
          <View style={styles.iconContainer}>
            <MaterialCommunityIcons name="camera-plus" size={32} color={colors.accent} />
          </View>
          <View style={styles.uploadContent}>
            <Text style={styles.uploadTitle}>饮食拍照</Text>
            <Text style={styles.uploadSubtitle}>上传照片后识别食物热量</Text>
          </View>
          <MaterialCommunityIcons name="chevron-right" size={24} color={colors.muted} />
        </TouchableOpacity>

        <MetricCard label="记账" value="¥0.00" accent="amber" />
        <MetricCard label="事件" value="0 条" accent="blue" />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  iconContainer: {
    alignItems: "center",
    backgroundColor: `${colors.accent}10`,
    borderRadius: 12,
    height: 56,
    justifyContent: "center",
    width: 56,
  },
  uploadCard: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: "row",
    padding: 16,
  },
  uploadContent: {
    flex: 1,
    marginLeft: 16,
  },
  uploadSubtitle: {
    color: colors.muted,
    fontSize: 13,
    marginTop: 2,
  },
  uploadTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700",
  },
});
