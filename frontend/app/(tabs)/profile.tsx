import { useEffect, useMemo, useState } from "react";
import { Ionicons } from "@expo/vector-icons";
import * as ImagePicker from "expo-image-picker";
import { router } from "expo-router";
import {
  ActivityIndicator,
  Alert,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";

import { CachedAvatar } from "@/components/CachedAvatar";
import { Screen } from "@/components/Screen";
import { useAuthStore } from "@/store/authStore";
import { colors } from "@/theme/colors";
import { authService } from "@/services/authService";
import { mediaService } from "@/services/mediaService";
import { syncService } from "@/services/syncService";
import { syncStateRepository } from "@/db/syncStateRepository";
import { syncMutationRepository } from "@/db/syncMutationRepository";
import { formatDateTimeCn } from "@/utils/time";

const APP_VERSION = "1.0.0";

export default function ProfileScreen() {
  const { user, clearAuth, updateUser } = useAuthStore();
  const [lastSync, setLastSync] = useState<string | null>(null);
  const [pendingCount, setPendingCount] = useState(0);
  const [isSyncing, setIsSyncing] = useState(false);
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [isChangingPassword, setIsChangingPassword] = useState(false);
  const [isCheckingUpdate, setIsCheckingUpdate] = useState(false);
  const [profileModalVisible, setProfileModalVisible] = useState(false);
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [displayNameDraft, setDisplayNameDraft] = useState(user?.displayName ?? "");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const avatarInitial = useMemo(() => (user?.displayName || user?.email || "我").slice(0, 1).toUpperCase(), [user]);

  useEffect(() => {
    loadSyncStatus();
  }, []);

  useEffect(() => {
    if (!profileModalVisible) {
      setDisplayNameDraft(user?.displayName ?? "");
    }
  }, [profileModalVisible, user?.displayName]);

  const loadSyncStatus = async () => {
    const [time, pending] = await Promise.all([
      syncStateRepository.getValue("last_sync_time"),
      syncMutationRepository.getPendingCount(),
    ]);
    setLastSync(time ? formatDateTimeCn(time) : "从未同步");
    setPendingCount(pending);
  };

  const clearLocalAvatarState = (nextAvatarAssetId?: string | null) => {
    if (!user) {
      return;
    }
    updateUser({
      ...user,
      avatarAssetId: nextAvatarAssetId ?? null,
      avatarUrl: null
    });
  };

  const handleSync = async () => {
    setIsSyncing(true);
    try {
      const success = await syncService.runSync();
      await loadSyncStatus();
      Alert.alert(success ? "同步完成" : "同步失败", success
        ? "本地数据已经和服务器同步。"
        : "当前可能没有网络或服务器不可用。离线记录会保留在本地，请恢复网络后再次点击立即同步。");
    } finally {
      setIsSyncing(false);
    }
  };

  const handlePickAvatar = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert("无法选择头像", "请允许访问相册后再试。");
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.86,
    });
    if (result.canceled || !result.assets[0]) return;

    const asset = result.assets[0];
    setIsUploadingAvatar(true);
    try {
      const uploaded = await mediaService.uploadImage(asset.uri, {
        purpose: "avatar",
        width: asset.width,
        height: asset.height,
        fileSize: asset.fileSize,
        type: asset.mimeType ?? "image/jpeg",
      });
      clearLocalAvatarState(uploaded.id);
      const updated = await authService.updateProfile({ avatarAssetId: uploaded.id });
      updateUser(updated);
      Alert.alert("头像已更新", "新的头像已经保存。");
    } catch (error) {
      if (user) {
        updateUser(user);
      }
      Alert.alert("头像上传失败", error instanceof Error ? error.message : "请稍后重试。");
    } finally {
      setIsUploadingAvatar(false);
    }
  };

  const handleRemoveAvatar = async () => {
    try {
      clearLocalAvatarState(null);
      const updated = await authService.updateProfile({ avatarAssetId: "" });
      updateUser(updated);
      Alert.alert("已移除头像", "现在会显示昵称首字。");
    } catch (error) {
      if (user) {
        updateUser(user);
      }
      Alert.alert("操作失败", error instanceof Error ? error.message : "请稍后重试。");
    }
  };

  const handleSaveProfile = async () => {
    const displayName = displayNameDraft.trim();
    if (!displayName) {
      Alert.alert("提示", "昵称不能为空。");
      return;
    }
    setIsSavingProfile(true);
    try {
      const updated = await authService.updateProfile({ displayName });
      updateUser(updated);
      setProfileModalVisible(false);
      Alert.alert("已保存", "昵称已更新。");
    } catch (error) {
      Alert.alert("保存失败", error instanceof Error ? error.message : "请稍后重试。");
    } finally {
      setIsSavingProfile(false);
    }
  };

  const handleChangePassword = async () => {
    if (!currentPassword || !newPassword || !confirmPassword) {
      Alert.alert("提示", "请完整填写密码。");
      return;
    }
    if (newPassword.length < 6) {
      Alert.alert("提示", "新密码至少 6 位。");
      return;
    }
    if (newPassword !== confirmPassword) {
      Alert.alert("提示", "两次输入的新密码不一致。");
      return;
    }
    setIsChangingPassword(true);
    try {
      await authService.changePassword({ currentPassword, newPassword });
      setPasswordModalVisible(false);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      Alert.alert("密码已修改", "下次登录请使用新密码。");
    } catch (error) {
      Alert.alert("修改失败", error instanceof Error ? error.message : "请稍后重试。");
    } finally {
      setIsChangingPassword(false);
    }
  };

  const handleCheckUpdate = async () => {
    setIsCheckingUpdate(true);
    try {
      await new Promise((resolve) => setTimeout(resolve, 650));
      Alert.alert("已是最新版本", `当前版本 ${APP_VERSION}，暂未发现可用更新。`);
    } finally {
      setIsCheckingUpdate(false);
    }
  };

  const handleLogout = async () => {
    try {
      await authService.logout();
    } catch (error) {
      console.error("退出登录失败：", error);
    } finally {
      clearAuth();
    }
  };

  return (
    <Screen title="我的" contentContainerStyle={styles.screenContent}>
      <View style={styles.hero}>
        <View style={styles.avatarWrap}>
          {user?.avatarUrl ? (
            <CachedAvatar
              uri={user.avatarUrl}
              cacheKey={user.avatarAssetId ?? user.id}
              style={styles.avatarImage}
            />
          ) : (
            <Text style={styles.avatarInitial}>{avatarInitial}</Text>
          )}
          <TouchableOpacity style={styles.avatarEditButton} onPress={handlePickAvatar} disabled={isUploadingAvatar}>
            {isUploadingAvatar ? (
              <ActivityIndicator size="small" color={colors.surface} />
            ) : (
              <Ionicons name="camera-outline" size={17} color={colors.surface} />
            )}
          </TouchableOpacity>
        </View>
        <View style={styles.heroText}>
          <Text style={styles.name}>{user?.displayName || "未登录"}</Text>
          <Text style={styles.email}>{user?.email || "-"}</Text>
        </View>
        <TouchableOpacity style={styles.heroEditButton} onPress={() => setProfileModalVisible(true)}>
          <Ionicons name="create-outline" size={18} color={colors.accent} />
          <Text style={styles.heroEditText}>编辑</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.quickGrid}>
        <InfoTile label="隐私状态" value="默认私密" icon="shield-checkmark-outline" />
        <InfoTile label="待同步" value={`${pendingCount} 条`} icon="cloud-upload-outline" danger={pendingCount > 0} />
        <InfoTile label="版本" value={APP_VERSION} icon="phone-portrait-outline" />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>智能与数据</Text>
        <ActionRow
          icon="sparkles-outline"
          title="AI 生活助手"
          subtitle="建议、对话与长期记忆"
          onPress={() => router.push("/ai")}
        />
        <ActionRow
          icon="sync-outline"
          title="立即同步"
          subtitle={`上次同步：${lastSync ?? "读取中"}`}
          loading={isSyncing}
          badge={pendingCount > 0 ? `${pendingCount}` : undefined}
          onPress={handleSync}
        />
      </View>

      {pendingCount > 0 ? (
        <View style={styles.offlineBanner}>
          <Ionicons name="cloud-offline-outline" size={18} color="#9A3412" />
          <Text style={styles.offlineText}>有离线记录尚未上传。恢复网络后请点击立即同步。</Text>
        </View>
      ) : null}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>账号安全</Text>
        <ActionRow
          icon="key-outline"
          title="修改密码"
          subtitle="定期更新密码可以提升账号安全"
          onPress={() => setPasswordModalVisible(true)}
        />
        <ActionRow
          icon="download-outline"
          title="检查更新"
          subtitle={`当前版本 ${APP_VERSION}`}
          loading={isCheckingUpdate}
          onPress={handleCheckUpdate}
        />
      </View>

      <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
        <Ionicons name="log-out-outline" size={18} color={colors.error} />
        <Text style={styles.logoutText}>退出登录</Text>
      </TouchableOpacity>

      <Modal visible={profileModalVisible} transparent animationType="fade" onRequestClose={() => setProfileModalVisible(false)}>
        <Pressable style={styles.modalOverlay} onPress={() => setProfileModalVisible(false)}>
          <Pressable style={styles.modalCard} onPress={() => {}}>
            <Text style={styles.modalTitle}>编辑资料</Text>
            <Text style={styles.fieldLabel}>昵称</Text>
            <TextInput
              style={styles.input}
              value={displayNameDraft}
              onChangeText={setDisplayNameDraft}
              placeholder="请输入昵称"
              placeholderTextColor={colors.muted}
              maxLength={50}
            />
            <View style={styles.modalActionRow}>
              {user?.avatarUrl ? (
                <TouchableOpacity style={styles.secondaryButton} onPress={handleRemoveAvatar}>
                  <Text style={styles.secondaryButtonText}>移除头像</Text>
                </TouchableOpacity>
              ) : null}
              <TouchableOpacity style={styles.secondaryButton} onPress={() => setProfileModalVisible(false)}>
                <Text style={styles.secondaryButtonText}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.primaryButton} onPress={handleSaveProfile} disabled={isSavingProfile}>
                {isSavingProfile ? <ActivityIndicator color={colors.surface} /> : <Text style={styles.primaryButtonText}>保存</Text>}
              </TouchableOpacity>
            </View>
          </Pressable>
        </Pressable>
      </Modal>

      <Modal visible={passwordModalVisible} transparent animationType="fade" onRequestClose={() => setPasswordModalVisible(false)}>
        <Pressable style={styles.modalOverlay} onPress={() => setPasswordModalVisible(false)}>
          <Pressable style={styles.modalCard} onPress={() => {}}>
            <Text style={styles.modalTitle}>修改密码</Text>
            <PasswordInput label="当前密码" value={currentPassword} onChangeText={setCurrentPassword} />
            <PasswordInput label="新密码" value={newPassword} onChangeText={setNewPassword} />
            <PasswordInput label="确认新密码" value={confirmPassword} onChangeText={setConfirmPassword} />
            <View style={styles.modalActionRow}>
              <TouchableOpacity style={styles.secondaryButton} onPress={() => setPasswordModalVisible(false)}>
                <Text style={styles.secondaryButtonText}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.primaryButton} onPress={handleChangePassword} disabled={isChangingPassword}>
                {isChangingPassword ? <ActivityIndicator color={colors.surface} /> : <Text style={styles.primaryButtonText}>确认修改</Text>}
              </TouchableOpacity>
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </Screen>
  );
}

function InfoTile({ label, value, icon, danger = false }: { label: string; value: string; icon: keyof typeof Ionicons.glyphMap; danger?: boolean }) {
  return (
    <View style={styles.infoTile}>
      <Ionicons name={icon} size={18} color={danger ? colors.error : colors.accent} />
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={[styles.infoValue, danger && styles.dangerText]}>{value}</Text>
    </View>
  );
}

function ActionRow({
  icon,
  title,
  subtitle,
  badge,
  loading,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  subtitle: string;
  badge?: string;
  loading?: boolean;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity style={styles.actionRow} onPress={onPress} disabled={loading} activeOpacity={0.82}>
      <View style={styles.actionIcon}>
        <Ionicons name={icon} size={20} color={colors.accent} />
      </View>
      <View style={styles.actionText}>
        <Text style={styles.actionTitle}>{title}</Text>
        <Text style={styles.actionSubtitle}>{subtitle}</Text>
      </View>
      {badge ? <Text style={styles.badge}>{badge}</Text> : null}
      {loading ? <ActivityIndicator size="small" color={colors.accent} /> : <Ionicons name="chevron-forward" size={20} color={colors.muted} />}
    </TouchableOpacity>
  );
}

function PasswordInput({ label, value, onChangeText }: { label: string; value: string; onChangeText: (value: string) => void }) {
  return (
    <View style={styles.passwordField}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        style={styles.input}
        value={value}
        onChangeText={onChangeText}
        placeholder={label}
        placeholderTextColor={colors.muted}
        secureTextEntry
      />
    </View>
  );
}

const styles = StyleSheet.create({
  actionIcon: {
    alignItems: "center",
    backgroundColor: "#ECFDF5",
    borderRadius: 10,
    height: 40,
    justifyContent: "center",
    width: 40,
  },
  actionRow: {
    alignItems: "center",
    borderTopColor: colors.border,
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: "row",
    gap: 12,
    minHeight: 72,
    paddingVertical: 12,
  },
  actionSubtitle: {
    color: colors.muted,
    fontSize: 13,
    lineHeight: 18,
  },
  actionText: {
    flex: 1,
    gap: 2,
  },
  actionTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700",
  },
  avatarEditButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderColor: colors.surface,
    borderRadius: 16,
    borderWidth: 2,
    bottom: -2,
    height: 32,
    justifyContent: "center",
    position: "absolute",
    right: -2,
    width: 32,
  },
  avatarImage: {
    height: "100%",
    width: "100%",
  },
  avatarInitial: {
    color: colors.surface,
    fontSize: 34,
    fontWeight: "800",
  },
  avatarWrap: {
    alignItems: "center",
    backgroundColor: colors.text,
    borderRadius: 36,
    height: 72,
    justifyContent: "center",
    overflow: "hidden",
    width: 72,
  },
  badge: {
    backgroundColor: colors.error,
    borderRadius: 999,
    color: colors.surface,
    fontSize: 12,
    fontWeight: "800",
    minWidth: 22,
    overflow: "hidden",
    paddingHorizontal: 7,
    paddingVertical: 3,
    textAlign: "center",
  },
  dangerText: {
    color: colors.error,
  },
  email: {
    color: colors.muted,
    fontSize: 14,
  },
  fieldLabel: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: "700",
    marginBottom: 8,
  },
  hero: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: "row",
    gap: 14,
    padding: 16,
  },
  heroEditButton: {
    alignItems: "center",
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: "row",
    gap: 5,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  heroEditText: {
    color: colors.accent,
    fontSize: 14,
    fontWeight: "700",
  },
  heroText: {
    flex: 1,
    gap: 4,
  },
  infoLabel: {
    color: colors.muted,
    fontSize: 12,
  },
  infoTile: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    flex: 1,
    gap: 6,
    minHeight: 94,
    padding: 12,
  },
  infoValue: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "800",
  },
  input: {
    backgroundColor: colors.background,
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    color: colors.text,
    fontSize: 16,
    minHeight: 46,
    paddingHorizontal: 12,
  },
  logoutButton: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: "#FECACA",
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: "row",
    gap: 8,
    justifyContent: "center",
    minHeight: 50,
  },
  logoutText: {
    color: colors.error,
    fontSize: 16,
    fontWeight: "700",
  },
  modalActionRow: {
    alignItems: "center",
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
    justifyContent: "flex-end",
    marginTop: 18,
  },
  modalCard: {
    backgroundColor: colors.surface,
    borderRadius: 8,
    maxWidth: 420,
    padding: 18,
    width: "92%",
  },
  modalOverlay: {
    alignItems: "center",
    backgroundColor: "rgba(15, 23, 42, 0.38)",
    flex: 1,
    justifyContent: "center",
    padding: 18,
  },
  modalTitle: {
    color: colors.text,
    fontSize: 20,
    fontWeight: "800",
    marginBottom: 16,
  },
  name: {
    color: colors.text,
    fontSize: 22,
    fontWeight: "800",
  },
  offlineBanner: {
    alignItems: "center",
    backgroundColor: "#FFF7ED",
    borderColor: "#FDBA74",
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: "row",
    gap: 8,
    padding: 12,
  },
  offlineText: {
    color: "#9A3412",
    flex: 1,
    fontSize: 13,
    lineHeight: 18,
  },
  passwordField: {
    marginBottom: 12,
  },
  primaryButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 8,
    minHeight: 42,
    minWidth: 88,
    justifyContent: "center",
    paddingHorizontal: 14,
  },
  primaryButtonText: {
    color: colors.surface,
    fontSize: 15,
    fontWeight: "800",
  },
  quickGrid: {
    flexDirection: "row",
    gap: 10,
  },
  screenContent: {
    gap: 14,
  },
  secondaryButton: {
    alignItems: "center",
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    minHeight: 42,
    justifyContent: "center",
    paddingHorizontal: 14,
  },
  secondaryButtonText: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "700",
  },
  section: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingTop: 14,
  },
  sectionTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "800",
    marginBottom: 4,
  },
});
