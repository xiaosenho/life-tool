import { View, TouchableOpacity, Text, StyleSheet } from "react-native";
import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";
import { useAuthStore } from "@/store/authStore";
import { colors } from "@/theme/colors";
import { authService } from "@/services/authService";

export default function ProfileScreen() {
  const { user, clearAuth } = useAuthStore();

  const handleLogout = async () => {
    try {
      await authService.logout();
      clearAuth();
    } catch (error) {
      console.error("Logout failed", error);
    }
  };

  return (
    <Screen title="我的">
      <View style={{ gap: 12 }}>
        <MetricCard
          label="账号"
          value={user?.displayName || "未登录"}
          accent="slate"
        />
        <MetricCard
          label="邮箱"
          value={user?.email || "-"}
          accent="blue"
        />
        <MetricCard label="隐私" value="默认私密" accent="green" />

        <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
          <Text style={styles.logoutText}>退出登录</Text>
        </TouchableOpacity>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  logoutButton: {
    marginTop: 24,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.error,
    borderRadius: 12,
    padding: 16,
    alignItems: "center",
  },
  logoutText: {
    color: colors.error,
    fontSize: 16,
    fontWeight: "600",
  },
});
