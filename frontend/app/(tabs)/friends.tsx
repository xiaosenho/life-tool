import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, TouchableOpacity, TextInput, Alert, ActivityIndicator, ScrollView } from "react-native";
import { useFocusEffect } from "expo-router";

import { Screen } from "@/components/Screen";
import { colors } from "@/theme/colors";
import { friendService, FriendInfo, FriendRequest } from "@/services/friendService";
import { leaderboardService, LeaderboardResponse, LeaderboardEntry } from "@/services/leaderboardService";

type Tab = "friends" | "requests" | "leaderboard";

function formatDuration(seconds: number): string {
  if (seconds >= 3600) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return `${h} 小时 ${m} 分钟`;
  }
  const m = Math.floor(seconds / 60);
  return `${m} 分钟`;
}

export default function FriendsScreen() {
  const [activeTab, setActiveTab] = useState<Tab>("friends");

  return (
    <Screen title="好友">
      <View style={styles.tabBar}>
        {([
          { key: "friends" as const, label: "好友列表" },
          { key: "requests" as const, label: "好友请求" },
          { key: "leaderboard" as const, label: "排行榜" },
        ]).map((tab) => (
          <TouchableOpacity
            key={tab.key}
            style={[styles.tab, activeTab === tab.key && styles.tabActive]}
            onPress={() => setActiveTab(tab.key)}
          >
            <Text style={[styles.tabText, activeTab === tab.key && styles.tabTextActive]}>
              {tab.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {activeTab === "friends" && <FriendsTab />}
      {activeTab === "requests" && <RequestsTab />}
      {activeTab === "leaderboard" && <LeaderboardTab />}
    </Screen>
  );
}

function FriendsTab() {
  const [friends, setFriends] = useState<FriendInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [email, setEmail] = useState("");
  const [sending, setSending] = useState(false);

  const loadFriends = useCallback(async () => {
    setLoading(true);
    try {
      const data = await friendService.listFriends();
      setFriends(data);
    } catch {
      Alert.alert("错误", "加载好友列表失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => { loadFriends(); }, [loadFriends]));

  const handleSendRequest = async () => {
    if (!email.trim()) return;
    setSending(true);
    try {
      await friendService.sendRequest(email.trim());
      Alert.alert("成功", "好友请求已发送");
      setEmail("");
    } catch (e: any) {
      Alert.alert("错误", e.message ?? "发送请求失败");
    } finally {
      setSending(false);
    }
  };

  const handleRemove = (friendUserId: string, displayName: string) => {
    Alert.alert("确认删除", `确定要删除好友 ${displayName} 吗？`, [
      { text: "取消", style: "cancel" },
      {
        text: "删除", style: "destructive", onPress: async () => {
          try {
            await friendService.removeFriend(friendUserId);
            loadFriends();
          } catch {
            Alert.alert("错误", "删除好友失败");
          }
        },
      },
    ]);
  };

  if (loading) {
    return <ActivityIndicator style={{ marginTop: 40 }} color={colors.accent} />;
  }

  return (
    <ScrollView contentContainerStyle={styles.tabContent}>
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>添加好友</Text>
        <View style={styles.addRow}>
          <TextInput
            style={styles.input}
            placeholder="输入对方的邮箱..."
            value={email}
            onChangeText={setEmail}
            keyboardType="email-address"
            autoCapitalize="none"
          />
          <TouchableOpacity style={styles.addButton} onPress={handleSendRequest} disabled={sending}>
            <Text style={styles.addButtonText}>{sending ? "发送中..." : "添加"}</Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>我的好友 ({friends.length})</Text>
        {friends.length === 0 ? (
          <Text style={styles.emptyText}>还没有好友，通过邮箱添加你的朋友吧</Text>
        ) : (
          friends.map((f) => (
            <View key={f.userId} style={styles.friendItem}>
              <View style={styles.avatar}>
                <Text style={styles.avatarText}>{f.displayName.charAt(0).toUpperCase()}</Text>
              </View>
              <View style={styles.friendInfo}>
                <Text style={styles.friendName}>{f.displayName}</Text>
                <Text style={styles.friendEmail}>{f.email}</Text>
              </View>
              <TouchableOpacity style={styles.removeButton} onPress={() => handleRemove(f.userId, f.displayName)}>
                <Text style={styles.removeButtonText}>删除</Text>
              </TouchableOpacity>
            </View>
          ))
        )}
      </View>
    </ScrollView>
  );
}

function RequestsTab() {
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [loading, setLoading] = useState(true);

  const loadRequests = useCallback(async () => {
    setLoading(true);
    try {
      const data = await friendService.listRequests();
      setRequests(data);
    } catch {
      Alert.alert("错误", "加载好友请求失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => { loadRequests(); }, [loadRequests]));

  const handleAction = async (requestId: string, action: "accept" | "reject") => {
    try {
      await friendService.handleRequest(requestId, action);
      loadRequests();
    } catch (e: any) {
      Alert.alert("错误", e.message ?? "操作失败");
    }
  };

  if (loading) {
    return <ActivityIndicator style={{ marginTop: 40 }} color={colors.accent} />;
  }

  return (
    <ScrollView contentContainerStyle={styles.tabContent}>
      {requests.length === 0 ? (
        <Text style={styles.emptyText}>暂无待处理的好友请求</Text>
      ) : (
        requests.filter((r) => r.status === "PENDING").map((r) => (
          <View key={r.id} style={styles.requestItem}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>?</Text>
            </View>
            <View style={styles.requestInfo}>
              <Text style={styles.requestLabel}>
                {r.fromUserId === "me" ? `发给 ${r.toUserId}` : `来自用户 ${r.fromUserId}`}
              </Text>
              <Text style={styles.requestStatus}>等待对方处理</Text>
            </View>
            {r.toUserId !== "me" && (
              <View style={styles.requestActions}>
                <TouchableOpacity style={styles.acceptButton} onPress={() => handleAction(r.id, "accept")}>
                  <Text style={styles.acceptButtonText}>接受</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.rejectButton} onPress={() => handleAction(r.id, "reject")}>
                  <Text style={styles.rejectButtonText}>拒绝</Text>
                </TouchableOpacity>
              </View>
            )}
          </View>
        ))
      )}
    </ScrollView>
  );
}

function LeaderboardTab() {
  const [lbTab, setLbTab] = useState<"focus" | "habits" | "streaks">("focus");
  const [focusPeriod, setFocusPeriod] = useState<"today" | "week">("today");
  const [data, setData] = useState<LeaderboardResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      let resp: LeaderboardResponse;
      if (lbTab === "focus") {
        resp = await leaderboardService.getFocus(focusPeriod);
      } else if (lbTab === "habits") {
        resp = await leaderboardService.getHabits();
      } else {
        resp = await leaderboardService.getStreaks();
      }
      setData(resp);
    } catch {
      Alert.alert("错误", "加载排行榜失败");
    } finally {
      setLoading(false);
    }
  }, [lbTab, focusPeriod]);

  useFocusEffect(useCallback(() => { loadData(); }, [loadData]));

  const metricLabel = (metric: string) => {
    switch (metric) {
      case "focus_seconds": return "专注时长";
      case "habit_completion": return "习惯完成";
      case "streak_days": return "连续打卡";
      default: return metric;
    }
  };

  const formatValue = (metric: string, value: number) => {
    if (metric === "focus_seconds") return formatDuration(value);
    return `${value}`;
  };

  return (
    <ScrollView contentContainerStyle={styles.tabContent}>
      <View style={styles.lbTabs}>
        {([
          { key: "focus" as const, label: "专注" },
          { key: "habits" as const, label: "习惯" },
          { key: "streaks" as const, label: "连续打卡" },
        ]).map((t) => (
          <TouchableOpacity
            key={t.key}
            style={[styles.lbTab, lbTab === t.key && styles.lbTabActive]}
            onPress={() => setLbTab(t.key)}
          >
            <Text style={[styles.lbTabText, lbTab === t.key && styles.lbTabTextActive]}>{t.label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {lbTab === "focus" && (
        <View style={styles.periodRow}>
          <TouchableOpacity
            style={[styles.periodBtn, focusPeriod === "today" && styles.periodBtnActive]}
            onPress={() => setFocusPeriod("today")}
          >
            <Text style={[styles.periodBtnText, focusPeriod === "today" && styles.periodBtnTextActive]}>今日</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.periodBtn, focusPeriod === "week" && styles.periodBtnActive]}
            onPress={() => setFocusPeriod("week")}
          >
            <Text style={[styles.periodBtnText, focusPeriod === "week" && styles.periodBtnTextActive]}>本周</Text>
          </TouchableOpacity>
        </View>
      )}

      {loading ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={colors.accent} />
      ) : data ? (
        <View style={styles.lbCard}>
          <Text style={styles.lbMetric}>{metricLabel(data.metric)}</Text>
          {data.entries.map((entry) => (
            <View key={entry.userId} style={styles.lbEntry}>
              <View style={styles.rankBadge}>
                <Text style={[styles.rankText, entry.rank === 1 && styles.rankGold, entry.rank === 2 && styles.rankSilver, entry.rank === 3 && styles.rankBronze]}>
                  {entry.rank}
                </Text>
              </View>
              <View style={styles.avatar}>
                <Text style={styles.avatarText}>{entry.displayName.charAt(0).toUpperCase()}</Text>
              </View>
              <Text style={styles.lbName}>{entry.displayName}</Text>
              <Text style={styles.lbValue}>{formatValue(data.metric, entry.value)}</Text>
            </View>
          ))}
        </View>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    flexDirection: "row",
    backgroundColor: colors.surface,
    borderRadius: 10,
    padding: 4,
    marginBottom: 16,
  },
  tab: {
    flex: 1,
    paddingVertical: 10,
    alignItems: "center",
    borderRadius: 8,
  },
  tabActive: {
    backgroundColor: colors.accent,
  },
  tabText: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.muted,
  },
  tabTextActive: {
    color: colors.surface,
  },
  tabContent: {
    paddingBottom: 24,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.text,
    marginBottom: 12,
  },
  addRow: {
    flexDirection: "row",
    gap: 10,
  },
  input: {
    flex: 1,
    backgroundColor: colors.surface,
    padding: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.border,
    color: colors.text,
  },
  addButton: {
    backgroundColor: colors.accent,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 10,
  },
  addButtonText: {
    color: colors.surface,
    fontWeight: "700",
    fontSize: 14,
  },
  emptyText: {
    textAlign: "center",
    color: colors.muted,
    marginTop: 30,
    fontSize: 14,
  },
  friendItem: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.surface,
    padding: 14,
    borderRadius: 12,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: colors.border,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.accent,
    justifyContent: "center",
    alignItems: "center",
    marginRight: 12,
  },
  avatarText: {
    color: colors.surface,
    fontSize: 16,
    fontWeight: "700",
  },
  friendInfo: {
    flex: 1,
  },
  friendName: {
    fontSize: 16,
    fontWeight: "600",
    color: colors.text,
  },
  friendEmail: {
    fontSize: 12,
    color: colors.muted,
    marginTop: 2,
  },
  removeButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: colors.error,
  },
  removeButtonText: {
    color: colors.error,
    fontSize: 13,
    fontWeight: "600",
  },
  requestItem: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.surface,
    padding: 14,
    borderRadius: 12,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: colors.border,
  },
  requestInfo: {
    flex: 1,
  },
  requestLabel: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.text,
  },
  requestStatus: {
    fontSize: 12,
    color: colors.muted,
    marginTop: 2,
  },
  requestActions: {
    flexDirection: "row",
    gap: 8,
  },
  acceptButton: {
    backgroundColor: colors.accent,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
  },
  acceptButtonText: {
    color: colors.surface,
    fontSize: 13,
    fontWeight: "700",
  },
  rejectButton: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
  },
  rejectButtonText: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: "600",
  },
  lbTabs: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 12,
  },
  lbTab: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  lbTabActive: {
    backgroundColor: colors.accent,
    borderColor: colors.accent,
  },
  lbTabText: {
    fontSize: 13,
    fontWeight: "600",
    color: colors.muted,
  },
  lbTabTextActive: {
    color: colors.surface,
  },
  periodRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 16,
  },
  periodBtn: {
    paddingHorizontal: 20,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  periodBtnActive: {
    backgroundColor: colors.accent,
    borderColor: colors.accent,
  },
  periodBtnText: {
    fontSize: 13,
    fontWeight: "600",
    color: colors.muted,
  },
  periodBtnTextActive: {
    color: colors.surface,
  },
  lbCard: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: colors.border,
  },
  lbMetric: {
    fontSize: 14,
    fontWeight: "700",
    color: colors.muted,
    marginBottom: 16,
  },
  lbEntry: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  rankBadge: {
    width: 28,
    alignItems: "center",
    marginRight: 8,
  },
  rankText: {
    fontSize: 16,
    fontWeight: "800",
    color: colors.muted,
  },
  rankGold: {
    color: "#D4A017",
  },
  rankSilver: {
    color: "#8C9196",
  },
  rankBronze: {
    color: "#A0522D",
  },
  lbName: {
    flex: 1,
    fontSize: 15,
    fontWeight: "600",
    color: colors.text,
  },
  lbValue: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.accent,
  },
});
