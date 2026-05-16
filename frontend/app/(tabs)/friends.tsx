import { Ionicons } from "@expo/vector-icons";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";
import { useFocusEffect, useRouter } from "expo-router";

import { Screen } from "@/components/Screen";
import {
  FriendConversationSummary,
  FriendInfo,
  FriendRequest,
  friendService
} from "@/services/friendService";
import { LeaderboardDetailResponse, leaderboardService } from "@/services/leaderboardService";
import { useAuthStore } from "@/store/authStore";
import { colors } from "@/theme/colors";
import { formatDateTimeCn } from "@/utils/time";
import { FRIEND_MESSAGE_TYPE_LABELS } from "@/services/friendService";

type TabKey = "friends" | "leaderboards" | "messages";
type BoardKey = "focus_today" | "focus_week" | "habits_today" | "streaks";

type BoardConfig = {
  key: BoardKey;
  label: string;
  unit: string;
};

const boardConfigs: BoardConfig[] = [
  { key: "focus_today", label: "今日专注", unit: "分钟" },
  { key: "focus_week", label: "本周专注", unit: "分钟" },
  { key: "habits_today", label: "习惯完成率", unit: "%" },
  { key: "streaks", label: "连续打卡", unit: "天" }
];

const boardDescriptions: Record<BoardKey, string> = {
  focus_today: "统计今天累计专注时长，数值越高排名越靠前。",
  focus_week: "统计最近 7 天累计专注时长，数值越高排名越靠前。",
  habits_today: "统计今天的习惯完成率，展示已完成习惯数占全部习惯数的比例。",
  streaks: "连续打卡表示连续多少天完成过至少 1 个习惯打卡，中断 1 天会重新开始计算。"
};

export default function FriendsScreen() {
  const router = useRouter();
  const userId = useAuthStore((state) => state.user?.id ?? "");
  const [activeTab, setActiveTab] = useState<TabKey>("friends");
  const [activeBoard, setActiveBoard] = useState<BoardKey>("focus_today");
  const [email, setEmail] = useState("");
  const [friends, setFriends] = useState<FriendInfo[]>([]);
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [conversations, setConversations] = useState<FriendConversationSummary[]>([]);
  const [leaderboards, setLeaderboards] = useState<Record<BoardKey, LeaderboardDetailResponse | null>>({
    focus_today: null,
    focus_week: null,
    habits_today: null,
    streaks: null
  });
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const hasMountedRef = useRef(false);

  const incomingRequests = useMemo(
    () => requests.filter((item) => item.status === "PENDING" && item.toUserId === userId),
    [requests, userId]
  );
  const outgoingRequests = useMemo(
    () => requests.filter((item) => item.status === "PENDING" && item.fromUserId === userId),
    [requests, userId]
  );

  const activeBoardData = leaderboards[activeBoard];

  const loadFriendData = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
    }
    try {
      const [friendRes, requestRes, conversationRes] = await Promise.all([
        friendService.listFriends(),
        friendService.listRequests(),
        friendService.listConversations()
      ]);

      if (friendRes.success && friendRes.data) setFriends(friendRes.data);
      if (requestRes.success && requestRes.data) setRequests(requestRes.data);
      if (conversationRes.success && conversationRes.data) setConversations(conversationRes.data);
    } catch (error) {
      Alert.alert("加载失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadMessagesData = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
    }
    try {
      const [friendRes, conversationRes] = await Promise.all([
        friendService.listFriends(),
        friendService.listConversations()
      ]);
      if (friendRes.success && friendRes.data) setFriends(friendRes.data);
      if (conversationRes.success && conversationRes.data) setConversations(conversationRes.data);
    } catch (error) {
      Alert.alert("加载失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadLeaderboardData = useCallback(async (boardKey: BoardKey, silent = false) => {
    if (!silent) {
      setLoading(true);
    }
    try {
      const response = await (async () => {
        switch (boardKey) {
          case "focus_today":
            return leaderboardService.getFocusDetail("today");
          case "focus_week":
            return leaderboardService.getFocusDetail("week");
          case "habits_today":
            return leaderboardService.getHabitsTodayDetail();
          case "streaks":
            return leaderboardService.getStreaksDetail();
        }
      })();
      setLeaderboards((current) => ({
        ...current,
        [boardKey]: response.success ? response.data ?? null : null
      }));
    } catch (error) {
      Alert.alert("加载失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadActiveTabData = useCallback(async (tab: TabKey, silent = false, boardKey = activeBoard) => {
    try {
      if (tab === "leaderboards") {
        await loadLeaderboardData(boardKey, silent);
      } else if (tab === "messages") {
        await loadMessagesData(silent);
      } else {
        await loadFriendData(silent);
      }
    } finally {
      setRefreshing(false);
    }
  }, [activeBoard, loadFriendData, loadLeaderboardData, loadMessagesData]);

  useFocusEffect(
    useCallback(() => {
      void loadActiveTabData(activeTab, true, activeBoard);
    }, [activeBoard, activeTab, loadActiveTabData])
  );

  useEffect(() => {
    if (!hasMountedRef.current) {
      hasMountedRef.current = true;
      return;
    }
    void loadActiveTabData(activeTab, true, activeBoard);
  }, [activeBoard, activeTab, loadActiveTabData]);

  async function handleRefresh() {
    setRefreshing(true);
    await loadActiveTabData(activeTab, true, activeBoard);
  }

  async function handleBoardPress(boardKey: BoardKey) {
    if (boardKey === activeBoard) {
      setRefreshing(true);
      await loadLeaderboardData(boardKey, true);
      setRefreshing(false);
      return;
    }
    setActiveBoard(boardKey);
  }

  async function handleSendRequest() {
    const targetEmail = email.trim();
    if (!targetEmail || submitting) return;

    setSubmitting(true);
    try {
      const response = await friendService.sendRequest(targetEmail);
      if (response.success) {
        setEmail("");
        await loadFriendData(true);
      } else {
        Alert.alert("添加失败", response.error?.message ?? "请稍后重试");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRequest(id: string, action: "accept" | "reject") {
    const response = action === "accept"
      ? await friendService.acceptRequest(id)
      : await friendService.rejectRequest(id);
    if (response.success) {
      await loadFriendData(true);
    } else {
      Alert.alert("处理失败", response.error?.message ?? "请稍后重试");
    }
  }

  async function handleRemoveFriend(friend: FriendInfo) {
    const response = await friendService.removeFriend(friend.userId);
    if (response.success) {
      await loadFriendData(true);
    } else {
      Alert.alert("删除失败", response.error?.message ?? "请稍后重试");
    }
  }

  return (
    <Screen
      title="好友"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} tintColor={colors.accent} />}
    >
      <View style={styles.tabRow}>
        <TabButton label="好友" active={activeTab === "friends"} onPress={() => setActiveTab("friends")} />
        <TabButton label="排行榜" active={activeTab === "leaderboards"} onPress={() => setActiveTab("leaderboards")} />
        <TabButton label="互动" active={activeTab === "messages"} onPress={() => setActiveTab("messages")} />
      </View>

      {activeTab === "friends" && (
        <>
          <View style={styles.panel}>
            <Text style={styles.panelTitle}>添加好友</Text>
            <View style={styles.inlineRow}>
              <TextInput
                style={styles.input}
                value={email}
                onChangeText={setEmail}
                placeholder="输入好友邮箱"
                keyboardType="email-address"
                autoCapitalize="none"
              />
              <TouchableOpacity
                style={[styles.primaryIconButton, submitting && styles.disabledButton]}
                onPress={handleSendRequest}
                disabled={submitting}
              >
                {submitting ? (
                  <ActivityIndicator size="small" color={colors.surface} />
                ) : (
                  <Ionicons name="person-add-outline" size={18} color={colors.surface} />
                )}
              </TouchableOpacity>
            </View>
          </View>

          <SectionHeader title="好友列表" meta={`${friends.length} 人`} />
          <View style={styles.list}>
            {friends.length === 0 ? (
              <EmptyState text="还没有好友，先邀请一位一起坚持吧。" />
            ) : (
              friends.map((friend) => {
                const conversation = conversations.find((item) => item.friendUserId === friend.userId);
                return (
                  <View key={friend.userId} style={styles.friendCard}>
                    <View style={styles.avatar}>
                      <Text style={styles.avatarText}>{friend.displayName.slice(0, 1)}</Text>
                    </View>
                    <View style={styles.friendInfo}>
                      <Text style={styles.friendName}>{friend.displayName}</Text>
                      <Text style={styles.friendMeta}>{friend.email}</Text>
                    </View>
                    <TouchableOpacity
                      style={styles.secondaryButton}
                      onPress={() => router.push({ pathname: "/friend-chat", params: { friendUserId: friend.userId, friendName: friend.displayName } })}
                    >
                      <Text style={styles.secondaryButtonText}>
                        {conversation?.unreadCount ? `互动(${conversation.unreadCount})` : "互动"}
                      </Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.iconButton} onPress={() => handleRemoveFriend(friend)}>
                      <Ionicons name="trash-outline" size={18} color={colors.error} />
                    </TouchableOpacity>
                  </View>
                );
              })
            )}
          </View>

          <SectionHeader title="好友申请" meta={`${incomingRequests.length + outgoingRequests.length} 条`} />
          <View style={styles.list}>
            {incomingRequests.map((request) => (
              <View key={request.id} style={styles.requestCard}>
                <View style={styles.flexBlock}>
                  <Text style={styles.requestTitle}>收到申请</Text>
                  <Text style={styles.friendMeta}>用户 {shortId(request.fromUserId)}</Text>
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
              <View key={request.id} style={styles.requestCard}>
                <View style={styles.flexBlock}>
                  <Text style={styles.requestTitle}>已发送</Text>
                  <Text style={styles.friendMeta}>用户 {shortId(request.toUserId)}</Text>
                </View>
                <Text style={styles.pendingText}>等待通过</Text>
              </View>
            ))}
            {incomingRequests.length === 0 && outgoingRequests.length === 0 && (
              <EmptyState text="暂无待处理申请。" />
            )}
          </View>
        </>
      )}

      {activeTab === "leaderboards" && (
        <>
          <View style={styles.boardChipRow}>
            {boardConfigs.map((board) => (
              <Pressable
                key={board.key}
                onPress={() => {
                  void handleBoardPress(board.key);
                }}
                style={[styles.boardChip, activeBoard === board.key && styles.boardChipActive]}
              >
                <Text style={[styles.boardChipText, activeBoard === board.key && styles.boardChipTextActive]}>
                  {board.label}
                </Text>
              </Pressable>
            ))}
          </View>

          <View style={styles.summaryCard}>
            <Text style={styles.panelTitle}>{boardConfigs.find((item) => item.key === activeBoard)?.label}</Text>
            {loading || !activeBoardData ? (
              <ActivityIndicator color={colors.accent} />
            ) : (
              <>
                <Text style={styles.summaryHighlight}>你当前第 {activeBoardData.self.rank} 名</Text>
                <Text style={styles.summaryText}>共 {activeBoardData.totalParticipants} 人参与</Text>
                <Text style={styles.summaryText}>
                  与前一名差距 {formatBoardValue(activeBoardData.metric, activeBoardData.gapToPrevious)} {boardConfigs.find((item) => item.key === activeBoard)?.unit}
                </Text>
                <Text style={styles.summaryHint}>{boardDescriptions[activeBoard]}</Text>
              </>
            )}
          </View>

          <SectionHeader title="完整榜单" meta={loading ? "加载中" : `${activeBoardData?.entries.length ?? 0} 人`} />
          <View style={styles.list}>
            {!activeBoardData || activeBoardData.entries.length === 0 ? (
              <EmptyState text="当前榜单还没有数据。" />
            ) : (
              activeBoardData.entries.map((entry) => (
                <View key={entry.userId} style={[styles.rankRow, entry.userId === userId && styles.rankRowActive]}>
                  <View style={styles.rankBadge}>
                    <Text style={styles.rankBadgeText}>{entry.rank}</Text>
                  </View>
                  <View style={styles.flexBlock}>
                    <Text style={styles.friendName}>{entry.displayName}</Text>
                    <Text style={styles.friendMeta}>
                      {entry.userId === userId ? "你" : "好友"} · {formatBoardValue(activeBoardData.metric, entry.value)} {boardConfigs.find((item) => item.key === activeBoard)?.unit}
                    </Text>
                  </View>
                </View>
              ))
            )}
          </View>
        </>
      )}

      {activeTab === "messages" && (
        <>
          <SectionHeader title="互动会话" meta={`${conversations.length} 个`} />
          <View style={styles.panel}>
            <Text style={styles.panelTitle}>快捷发起聊天</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.conversationTabs}>
              {friends.length === 0 ? (
                <EmptyState text="还没有好友，先去添加一位好友吧。" />
              ) : (
                friends.map((friend) => {
                  const conversation = conversations.find((item) => item.friendUserId === friend.userId);
                  return (
                    <Pressable
                      key={friend.userId}
                      style={styles.conversationChip}
                      onPress={() => router.push({ pathname: "/friend-chat", params: { friendUserId: friend.userId, friendName: friend.displayName } })}
                    >
                      <Text style={styles.conversationChipText}>{friend.displayName}</Text>
                      {!!conversation?.unreadCount && (
                        <View style={styles.unreadDot}>
                          <Text style={styles.unreadText}>{conversation.unreadCount}</Text>
                        </View>
                      )}
                    </Pressable>
                  );
                })
              )}
            </ScrollView>
          </View>

          <View style={styles.panel}>
            <Text style={styles.panelTitle}>最近互动</Text>
            {conversations.length === 0 ? (
              <Text style={styles.emptyText}>还没有互动消息，现在就可以直接给好友发第一条消息。</Text>
            ) : (
              conversations.map((item) => (
                <Pressable
                  key={item.friendUserId}
                  style={styles.conversationListItem}
                  onPress={() => router.push({ pathname: "/friend-chat", params: { friendUserId: item.friendUserId, friendName: item.friendDisplayName } })}
                >
                  <View style={styles.flexBlock}>
                    <Text style={styles.friendName}>{item.friendDisplayName}</Text>
                    <Text style={styles.friendMeta}>
                      {FRIEND_MESSAGE_TYPE_LABELS[item.lastMessageType]} · {formatDateTimeCn(item.lastMessageAt)}
                    </Text>
                    <Text style={styles.messagePreview} numberOfLines={1}>
                      {item.lastMessage}
                    </Text>
                  </View>
                  {item.unreadCount > 0 ? (
                    <View style={styles.unreadDot}>
                      <Text style={styles.unreadText}>{item.unreadCount}</Text>
                    </View>
                  ) : (
                    <Ionicons name="chevron-forward" size={18} color={colors.muted} />
                  )}
                </Pressable>
              ))
            )}
          </View>
        </>
      )}
    </Screen>
  );
}

function TabButton({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.tabButton, active && styles.tabButtonActive]}>
      <Text style={[styles.tabButtonText, active && styles.tabButtonTextActive]}>{label}</Text>
    </Pressable>
  );
}

function SectionHeader({ title, meta }: { title: string; meta?: string }) {
  return (
    <View style={styles.sectionHeader}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {meta ? <Text style={styles.sectionMeta}>{meta}</Text> : null}
    </View>
  );
}

function EmptyState({ text }: { text: string }) {
  return <Text style={styles.emptyText}>{text}</Text>;
}

function formatBoardValue(metric: string, value: number) {
  if (metric.startsWith("focus_seconds")) {
    return Math.round(value / 60).toString();
  }
  return value.toString();
}

function shortId(id: string) {
  return id.length > 8 ? id.slice(0, 8) : id;
}

const styles = StyleSheet.create({
  acceptButton: {
    backgroundColor: colors.accent,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  acceptButtonText: {
    color: colors.surface,
    fontSize: 13,
    fontWeight: "700"
  },
  avatar: {
    alignItems: "center",
    backgroundColor: "#CCFBF1",
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
  boardChip: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  boardChipActive: {
    backgroundColor: colors.accent
  },
  boardChipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10
  },
  boardChipText: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: "700"
  },
  boardChipTextActive: {
    color: colors.surface
  },
  conversationChip: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 999,
    borderWidth: 1,
    flexDirection: "row",
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  conversationChipText: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "700"
  },
  conversationListItem: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 18,
    borderWidth: 1,
    flexDirection: "row",
    gap: 12,
    padding: 14
  },
  conversationTabs: {
    flexDirection: "row",
    gap: 10
  },
  disabledButton: {
    opacity: 0.5
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 21
  },
  flexBlock: {
    flex: 1,
    gap: 4
  },
  friendCard: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 18,
    borderWidth: 1,
    flexDirection: "row",
    gap: 12,
    padding: 14
  },
  friendInfo: {
    flex: 1,
    gap: 3
  },
  friendMeta: {
    color: colors.muted,
    fontSize: 12
  },
  friendName: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "800"
  },
  iconButton: {
    alignItems: "center",
    height: 36,
    justifyContent: "center",
    width: 36
  },
  inlineRow: {
    alignItems: "center",
    flexDirection: "row",
    gap: 10
  },
  input: {
    backgroundColor: colors.background,
    borderColor: colors.border,
    borderRadius: 14,
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
  messagePreview: {
    color: colors.muted,
    fontSize: 13,
    marginTop: 2
  },
  panel: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 18,
    borderWidth: 1,
    gap: 8,
    marginTop: 18,
    padding: 16
  },
  panelTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "800"
  },
  pendingText: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: "700"
  },
  primaryIconButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 14,
    height: 46,
    justifyContent: "center",
    width: 46
  },
  rankBadge: {
    alignItems: "center",
    backgroundColor: "#ECFEFF",
    borderRadius: 14,
    height: 28,
    justifyContent: "center",
    width: 28
  },
  rankBadgeText: {
    color: colors.accent,
    fontSize: 13,
    fontWeight: "800"
  },
  rankRow: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 16,
    borderWidth: 1,
    flexDirection: "row",
    gap: 12,
    padding: 14
  },
  rankRowActive: {
    borderColor: colors.accent,
    backgroundColor: "#F0FDFA"
  },
  rejectButton: {
    backgroundColor: "#FEF2F2",
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  rejectButtonText: {
    color: colors.error,
    fontSize: 13,
    fontWeight: "700"
  },
  requestCard: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 16,
    borderWidth: 1,
    flexDirection: "row",
    gap: 10,
    padding: 14
  },
  requestTitle: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "700"
  },
  secondaryButton: {
    backgroundColor: "#ECFDF5",
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  secondaryButtonText: {
    color: colors.accent,
    fontSize: 13,
    fontWeight: "700"
  },
  sectionHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 10,
    marginTop: 18
  },
  sectionMeta: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: "700"
  },
  sectionTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "800"
  },
  summaryCard: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 18,
    borderWidth: 1,
    gap: 8,
    marginTop: 18,
    padding: 18
  },
  summaryHighlight: {
    color: colors.accent,
    fontSize: 24,
    fontWeight: "800"
  },
  summaryHint: {
    color: colors.text,
    fontSize: 13,
    lineHeight: 20,
    marginTop: 6
  },
  summaryText: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 21
  },
  tabButton: {
    borderColor: colors.border,
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 10
  },
  tabButtonActive: {
    backgroundColor: colors.text,
    borderColor: colors.text
  },
  tabButtonText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "700"
  },
  tabButtonTextActive: {
    color: colors.surface
  },
  tabRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10
  },
  unreadDot: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 999,
    justifyContent: "center",
    minWidth: 20,
    paddingHorizontal: 6,
    paddingVertical: 2
  },
  unreadText: {
    color: colors.surface,
    fontSize: 11,
    fontWeight: "800"
  }
});
