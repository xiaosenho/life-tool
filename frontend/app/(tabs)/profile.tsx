import { useState, useEffect } from "react";
import { Ionicons } from "@expo/vector-icons";
import { router } from "expo-router";
import { View, TouchableOpacity, Text, StyleSheet, ActivityIndicator, Alert } from "react-native";
import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";
import { useAuthStore } from "@/store/authStore";
import { colors } from "@/theme/colors";
import { authService } from "@/services/authService";
import { syncService } from "@/services/syncService";
import { syncStateRepository } from "@/db/syncStateRepository";
import { syncMutationRepository } from "@/db/syncMutationRepository";
import { formatDateTimeCn } from "@/utils/time";

export default function ProfileScreen() {
  const { user, clearAuth } = useAuthStore();
  const [lastSync, setLastSync] = useState<string | null>(null);
  const [pendingCount, setPendingCount] = useState(0);
  const [isSyncing, setIsSyncing] = useState(false);

  useEffect(() => {
    loadSyncStatus();
  }, []);

  const loadSyncStatus = async () => {
    const [time, pending] = await Promise.all([
      syncStateRepository.getValue('last_sync_time'),
      syncMutationRepository.getPendingCount(),
    ]);
    setLastSync(time ? formatDateTimeCn(time) : '从未同步');
    setPendingCount(pending);
  };

  const handleSync = async () => {
    setIsSyncing(true);
    try {
      const success = await syncService.runSync();
      if (success) {
        Alert.alert("成功", "数据同步完成");
        await loadSyncStatus();
      } else {
        await loadSyncStatus();
        Alert.alert("同步失败", "当前可能没有网络或服务器不可用。离线记录会保留在本地，请恢复网络后再次点击立即同步。");
      }
    } catch (error) {
      await loadSyncStatus();
      Alert.alert("同步失败", "同步过程发生异常。离线记录会保留在本地，请恢复网络后手动同步。");
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

        <TouchableOpacity style={styles.aiEntry} onPress={() => router.push("/ai")}>
          <View style={styles.aiIcon}>
            <Ionicons name="sparkles-outline" size={22} color={colors.accent} />
          </View>
          <View style={styles.aiTextBlock}>
            <Text style={styles.aiTitle}>AI 生活助手</Text>
            <Text style={styles.aiSubtitle}>建议、对话与长期记忆</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color={colors.muted} />
        </TouchableOpacity>

        <View style={styles.syncSection}>
          <View style={styles.syncInfo}>
            <Text style={styles.syncLabel}>上次同步：</Text>
            <Text style={styles.syncValue}>{lastSync}</Text>
          </View>
          <View style={styles.syncInfo}>
            <Text style={styles.syncLabel}>待同步：</Text>
            <Text style={[styles.syncValue, pendingCount > 0 && styles.pendingValue]}>
              {pendingCount} 条
            </Text>
          </View>
          {pendingCount > 0 && (
            <Text style={styles.offlineHint}>
              有离线记录尚未上传。恢复网络后请点击立即同步。
            </Text>
          )}
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
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: "row",
    gap: 12,
    padding: 14,
  },
  aiIcon: {
    alignItems: "center",
    backgroundColor: "#ECFDF5",
    borderRadius: 10,
    height: 42,
    justifyContent: "center",
    width: 42,
  },
  aiSubtitle: {
    color: colors.muted,
    fontSize: 13,
    lineHeight: 18,
  },
  aiTextBlock: {
    flex: 1,
    gap: 2,
  },
  aiTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700",
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
  offlineHint: {
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18,
    marginBottom: 12,
  },
  pendingValue: {
    color: colors.error,
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
