import { Ionicons } from "@expo/vector-icons";
import { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";

import { Screen } from "@/components/Screen";
import { FriendInfo, FriendRequest, friendService } from "@/services/friendService";
import { LeaderboardResponse, leaderboardService } from "@/services/leaderboardService";
import { useAuthStore } from "@/store/authStore";
import { colors } from "@/theme/colors";

type LeaderboardCard = {
  title: string;
  unit: string;
  data: LeaderboardResponse | null;
};

export default function FriendsScreen() {
  const userId = useAuthStore((state) => state.user?.id);
  const [email, setEmail] = useState("");
  const [friends, setFriends] = useState<FriendInfo[]>([]);
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [focusToday, setFocusToday] = useState<LeaderboardResponse | null>(null);
  const [focusWeek, setFocusWeek] = useState<LeaderboardResponse | null>(null);
  const [habitToday, setHabitToday] = useState<LeaderboardResponse | null>(null);
  const [streaks, setStreaks] = useState<LeaderboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const incomingRequests = useMemo(
    () => requests.filter((item) => item.status === "PENDING" && item.toUserId === userId),
    [requests, userId]
  );
  const outgoingRequests = useMemo(
    () => requests.filter((item) => item.status === "PENDING" && item.fromUserId === userId),
    [requests, userId]
  );

  const leaderboards: LeaderboardCard[] = [
    { title: "今日专注", unit: "分钟", data: focusToday },
    { title: "本周专注", unit: "分钟", data: focusWeek },
    { title: "习惯完成", unit: "%", data: habitToday },
    { title: "连续打卡", unit: "天", data: streaks }
  ];

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [
        friendRes,
        requestRes,
        focusTodayRes,
        focusWeekRes,
        habitTodayRes,
        streakRes
      ] = await Promise.all([
        friendService.listFriends(),
        friendService.listRequests(),
        leaderboardService.getFocus("today"),
        leaderboardService.getFocus("week"),
        leaderboardService.getHabitsToday(),
        leaderboardService.getStreaks()
      ]);

      if (friendRes.success && friendRes.data) setFriends(friendRes.data);
      if (requestRes.success && requestRes.data) setRequests(requestRes.data);
      if (focusTodayRes.success && focusTodayRes.data) setFocusToday(focusTodayRes.data);
      if (focusWeekRes.success && focusWeekRes.data) setFocusWeek(focusWeekRes.data);
      if (habitTodayRes.success && habitTodayRes.data) setHabitToday(habitTodayRes.data);
      if (streakRes.success && streakRes.data) setStreaks(streakRes.data);
    } catch (error) {
      Alert.alert("加载失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setLoading(false);
    }
  };

  const handleSendRequest = async () => {
    const targetEmail = email.trim();
    if (!targetEmail || submitting) return;

    setSubmitting(true);
    try {
      const response = await friendService.sendRequest(targetEmail);
      if (response.success) {
        setEmail("");
        await loadData();
      } else {
        Alert.alert("添加失败", response.error?.message ?? "请稍后重试");
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleRequest = async (id: string, action: "accept" | "reject") => {
    const response = action === "accept"
      ? await friendService.acceptRequest(id)
      : await friendService.rejectRequest(id);
    if (response.success) {
      await loadData();
    } else {
      Alert.alert("处理失败", response.error?.message ?? "请稍后重试");
    }
  };

  const handleRemoveFriend = async (friend: FriendInfo) => {
    const response = await friendService.removeFriend(friend.userId);
    if (response.success) {
      await loadData();
    } else {
      Alert.alert("删除失败", response.error?.message ?? "请稍后重试");
    }
  };

  return (
    <Screen title="好友">
      <View style={styles.searchPanel}>
        <TextInput
          style={styles.input}
          value={email}
          onChangeText={setEmail}
          placeholder="输入好友邮箱"
          keyboardType="email-address"
          autoCapitalize="none"
        />
        <TouchableOpacity
          style={[styles.addButton, submitting && styles.disabledButton]}
          onPress={handleSendRequest}
          disabled={submitting}
        >
          {submitting ? (
            <ActivityIndicator size="small" color={colors.surface} />
          ) : (
            <Ionicons name="person-add-outline" size={20} color={colors.surface} />
          )}
        </TouchableOpacity>
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>排行榜</Text>
        <TouchableOpacity style={styles.refreshButton} onPress={loadData} disabled={loading}>
          <Ionicons name="refresh" size={18} color={colors.accent} />
        </TouchableOpacity>
      </View>
      <View style={styles.rankGrid}>
        {leaderboards.map((item) => (
          <LeaderboardSummary key={item.title} item={item} loading={loading} />
        ))}
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>好友列表</Text>
        <Text style={styles.sectionMeta}>{friends.length} 人</Text>
      </View>
      <View style={styles.list}>
        {friends.length === 0 ? (
          <Text style={styles.emptyText}>还没有好友。</Text>
        ) : (
          friends.map((friend) => (
            <View key={friend.userId} style={styles.friendItem}>
              <View style={styles.avatar}>
                <Text style={styles.avatarText}>{friend.displayName.slice(0, 1)}</Text>
              </View>
              <View style={styles.friendInfo}>
                <Text style={styles.friendName}>{friend.displayName}</Text>
                <Text style={styles.friendEmail}>{friend.email}</Text>
              </View>
              <TouchableOpacity style={styles.iconButton} onPress={() => handleRemoveFriend(friend)}>
                <Ionicons name="trash-outline" size={18} color={colors.error} />
              </TouchableOpacity>
            </View>
          ))
        )}
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>好友申请</Text>
        <Text style={styles.sectionMeta}>{incomingRequests.length + outgoingRequests.length} 条</Text>
      </View>
      <View style={styles.list}>
        {incomingRequests.map((request) => (
          <View key={request.id} style={styles.requestItem}>
            <View style={styles.requestTextBlock}>
              <Text style={styles.requestTitle}>收到申请</Text>
              <Text style={styles.requestMeta}>用户 {shortId(request.fromUserId)}</Text>
            </View>
            <TouchableOpacity style={styles.acceptButton} onPress={() => handleRequest(request.id, "accept")}>
              <Text style={styles.acceptButtonText}>通过</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.rejectButton} onPress={() => handleRequest(request.id, "reject")}>
              <Text style={styles.rejectButtonText}>拒绝</Text>
            </TouchableOpacity>
          </View>
        ))}
        {outgoingRequests.map((request) => (
          <View key={request.id} style={styles.requestItem}>
            <View style={styles.requestTextBlock}>
              <Text style={styles.requestTitle}>已发送</Text>
              <Text style={styles.requestMeta}>用户 {shortId(request.toUserId)}</Text>
            </View>
            <Text style={styles.pendingText}>等待通过</Text>
          </View>
        ))}
        {incomingRequests.length === 0 && outgoingRequests.length === 0 && (
          <Text style={styles.emptyText}>暂无待处理申请。</Text>
        )}
      </View>
    </Screen>
  );
}

function LeaderboardSummary({ item, loading }: { item: LeaderboardCard; loading: boolean }) {
  const data = item.data;
  const top = data?.entries?.[0];
  const value = top && data ? formatValue(data.metric, top.value) : "--";

  return (
    <View style={styles.rankCard}>
      <Text style={styles.rankTitle}>{item.title}</Text>
      <Text style={styles.rankValue}>{loading ? "..." : value}</Text>
      <Text style={styles.rankMeta}>
        {top ? `第 ${top.rank} 名 · ${item.unit}` : item.unit}
      </Text>
    </View>
  );
}

function formatValue(metric: string, value: number) {
  if (metric === "focus_seconds") return Math.round(value / 60).toString();
  return value.toString();
}

function shortId(id: string) {
  return id.length > 8 ? id.slice(0, 8) : id;
}

const styles = StyleSheet.create({
  acceptButton: {
    backgroundColor: colors.accent,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  acceptButtonText: {
    color: colors.surface,
    fontSize: 13,
    fontWeight: "700"
  },
  addButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 12,
    height: 46,
    justifyContent: "center",
    width: 46
  },
  avatar: {
    alignItems: "center",
    backgroundColor: "#ECFDF5",
    borderRadius: 20,
    height: 40,
    justifyContent: "center",
    width: 40
  },
  avatarText: {
    color: colors.accent,
    fontSize: 16,
    fontWeight: "800"
  },
  disabledButton: {
    opacity: 0.55
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 20
  },
  friendEmail: {
    color: colors.muted,
    fontSize: 12
  },
  friendInfo: {
    flex: 1,
    gap: 3
  },
  friendItem: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: "row",
    gap: 12,
    padding: 12
  },
  friendName: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "700"
  },
  iconButton: {
    alignItems: "center",
    height: 36,
    justifyContent: "center",
    width: 36
  },
  input: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    color: colors.text,
    flex: 1,
    fontSize: 14,
    height: 46,
    paddingHorizontal: 12
  },
  list: {
    gap: 10
  },
  pendingText: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: "600"
  },
  rankCard: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flex: 1,
    minWidth: "47%",
    padding: 14
  },
  rankGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10
  },
  rankMeta: {
    color: colors.muted,
    fontSize: 12
  },
  rankTitle: {
    color: colors.muted,
    fontSize: 13
  },
  rankValue: {
    color: colors.text,
    fontSize: 24,
    fontWeight: "800",
    marginVertical: 6
  },
  refreshButton: {
    alignItems: "center",
    height: 34,
    justifyContent: "center",
    width: 34
  },
  rejectButton: {
    backgroundColor: "#FEF2F2",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  rejectButtonText: {
    color: colors.error,
    fontSize: 13,
    fontWeight: "700"
  },
  requestItem: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: "row",
    gap: 10,
    padding: 12
  },
  requestMeta: {
    color: colors.muted,
    fontSize: 12
  },
  requestTextBlock: {
    flex: 1,
    gap: 3
  },
  requestTitle: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "700"
  },
  searchPanel: {
    alignItems: "center",
    flexDirection: "row",
    gap: 10
  },
  sectionHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 10,
    marginTop: 22
  },
  sectionMeta: {
    color: colors.muted,
    fontSize: 12
  },
  sectionTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "800"
  }
});
