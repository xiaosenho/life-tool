import { useState, useEffect } from "react";
import { View, TouchableOpacity, Text, StyleSheet, ActivityIndicator, Alert } from "react-native";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";
import { useAuthStore } from "@/store/authStore";
import { colors } from "@/theme/colors";
import { authService } from "@/services/authService";
import { syncService } from "@/services/syncService";
import { syncStateRepository } from "@/db/syncStateRepository";

export default function ProfileScreen() {
  const { user, clearAuth } = useAuthStore();
  const router = useRouter();
  const [lastSync, setLastSync] = useState<string | null>(null);
  const [isSyncing, setIsSyncing] = useState(false);

  useEffect(() => {
    loadSyncStatus();
  }, []);

  const loadSyncStatus = async () => {
    const time = await syncStateRepository.getValue('last_sync_time');
    setLastSync(time ? new Date(time).toLocaleString() : '从未同步');
  };

  const handleSync = async () => {
    setIsSyncing(true);
    try {
      const success = await syncService.runSync();
      if (success) {
        Alert.alert("成功", "数据同步完成");
        await loadSyncStatus();
      } else {
        Alert.alert("错误", "同步失败，请稍后再试");
      }
    } catch (error) {
      Alert.alert("错误", "同步过程发生异常");
    } finally {
      setIsSyncing(false);
    }
  };

  const handleLogout = async () => {
    try {
      await authService.logout();
      clearAuth();
    } catch (error) {
      console.error("退出登录失败：", error);
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

        <TouchableOpacity style={styles.aiEntry} onPress={() => router.push("/ai-chat")}>
          <Ionicons name="bulb-outline" size={24} color={colors.accent} />
          <View style={{ flex: 1 }}>
            <Text style={styles.aiEntryTitle}>AI 助手</Text>
            <Text style={styles.aiEntrySubtitle}>获取生活建议和智能对话</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color={colors.muted} />
        </TouchableOpacity>

        <View style={styles.syncSection}>
          <View style={styles.syncInfo}>
            <Text style={styles.syncLabel}>上次同步：</Text>
            <Text style={styles.syncValue}>{lastSync}</Text>
          </View>
          <TouchableOpacity
            style={[styles.syncButton, isSyncing && styles.disabledButton]}
            onPress={handleSync}
            disabled={isSyncing}
          >
            {isSyncing ? (
              <ActivityIndicator size="small" color={colors.accent} />
            ) : (
              <Text style={styles.syncButtonText}>立即同步</Text>
            )}
          </TouchableOpacity>
        </View>

        <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
          <Text style={styles.logoutText}>退出登录</Text>
        </TouchableOpacity>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  aiEntry: {
    alignItems: "center",
    backgroundColor: `${colors.accent}08`,
    borderColor: `${colors.accent}20`,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: "row",
    gap: 12,
    marginTop: 12,
    padding: 16,
  },
  aiEntryTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700",
  },
  aiEntrySubtitle: {
    color: colors.muted,
    fontSize: 12,
    marginTop: 2,
  },
  syncSection: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: 16,
    marginTop: 12,
  },
  syncInfo: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  syncLabel: {
    fontSize: 14,
    color: colors.muted,
  },
  syncValue: {
    fontSize: 14,
    color: colors.text,
    fontWeight: '500',
  },
  syncButton: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.accent,
    borderRadius: 8,
    padding: 10,
    alignItems: 'center',
  },
  disabledButton: {
    opacity: 0.5,
  },
  syncButtonText: {
    color: colors.accent,
    fontSize: 14,
    fontWeight: '600',
  },
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
