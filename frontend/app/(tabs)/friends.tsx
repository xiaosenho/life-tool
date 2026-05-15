import { Ionicons } from "@expo/vector-icons";
import { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";

import { Screen } from "@/components/Screen";
import {
  FriendConversationSummary,
  FriendInfo,
  FriendMessage,
  FriendRequest,
  friendService
} from "@/services/friendService";
import { LeaderboardDetailResponse, leaderboardService } from "@/services/leaderboardService";
import { useAuthStore } from "@/store/authStore";
import { colors } from "@/theme/colors";

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

export default function FriendsScreen() {
  const userId = useAuthStore((state) => state.user?.id ?? "");
  const [activeTab, setActiveTab] = useState<TabKey>("friends");
  const [activeBoard, setActiveBoard] = useState<BoardKey>("focus_today");
  const [email, setEmail] = useState("");
  const [messageDraft, setMessageDraft] = useState("");
  const [selectedFriendId, setSelectedFriendId] = useState<string | null>(null);
  const [friends, setFriends] = useState<FriendInfo[]>([]);
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [conversations, setConversations] = useState<FriendConversationSummary[]>([]);
  const [messages, setMessages] = useState<FriendMessage[]>([]);
  const [leaderboards, setLeaderboards] = useState<Record<BoardKey, LeaderboardDetailResponse | null>>({
    focus_today: null,
    focus_week: null,
    habits_today: null,
    streaks: null
  });
  const [loading, setLoading] = useState(true);
  const [messageLoading, setMessageLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const incomingRequests = useMemo(
    () => requests.filter((item) => item.status === "PENDING" && item.toUserId === userId),
    [requests, userId]
  );
  const outgoingRequests = useMemo(
    () => requests.filter((item) => item.status === "PENDING" && item.fromUserId === userId),
    [requests, userId]
  );

  const selectedConversation = useMemo(
    () => conversations.find((item) => item.friendUserId === selectedFriendId) ?? null,
    [conversations, selectedFriendId]
  );

  const selectedFriend = useMemo(
    () => friends.find((item) => item.userId === selectedFriendId) ?? null,
    [friends, selectedFriendId]
  );

  const activeBoardData = leaderboards[activeBoard];

  useEffect(() => {
    void loadData();
  }, []);

  useEffect(() => {
    if (!selectedFriendId && conversations.length > 0) {
      setSelectedFriendId(conversations[0].friendUserId);
    }
  }, [conversations, selectedFriendId]);

  useEffect(() => {
    if (!selectedFriendId) {
      setMessages([]);
      return;
    }
    void loadConversation(selectedFriendId);
  }, [selectedFriendId]);

  async function loadData() {
    setLoading(true);
    try {
      const [
        friendRes,
        requestRes,
        focusTodayRes,
        focusWeekRes,
        habitTodayRes,
        streakRes,
        conversationRes
      ] = await Promise.all([
        friendService.listFriends(),
        friendService.listRequests(),
        leaderboardService.getFocusDetail("today"),
        leaderboardService.getFocusDetail("week"),
        leaderboardService.getHabitsTodayDetail(),
        leaderboardService.getStreaksDetail(),
        friendService.listConversations()
      ]);

      if (friendRes.success && friendRes.data) setFriends(friendRes.data);
      if (requestRes.success && requestRes.data) setRequests(requestRes.data);
      if (conversationRes.success && conversationRes.data) setConversations(conversationRes.data);
      setLeaderboards({
        focus_today: focusTodayRes.success ? focusTodayRes.data ?? null : null,
        focus_week: focusWeekRes.success ? focusWeekRes.data ?? null : null,
        habits_today: habitTodayRes.success ? habitTodayRes.data ?? null : null,
        streaks: streakRes.success ? streakRes.data ?? null : null
      });
    } catch (error) {
      Alert.alert("加载失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setLoading(false);
    }
  }

  async function loadConversation(friendUserId: string) {
    setMessageLoading(true);
    try {
      const response = await friendService.listMessages(friendUserId);
      if (response.success && response.data) {
        setMessages(response.data);
        await friendService.markConversationRead(friendUserId);
        const conversationRes = await friendService.listConversations();
        if (conversationRes.success && conversationRes.data) {
          setConversations(conversationRes.data);
        }
      }
    } finally {
      setMessageLoading(false);
    }
  }

  async function handleSendRequest() {
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
  }

  async function handleRequest(id: string, action: "accept" | "reject") {
    const response = action === "accept"
      ? await friendService.acceptRequest(id)
      : await friendService.rejectRequest(id);
    if (response.success) {
      await loadData();
    } else {
      Alert.alert("处理失败", response.error?.message ?? "请稍后重试");
    }
  }

  async function handleRemoveFriend(friend: FriendInfo) {
    const response = await friendService.removeFriend(friend.userId);
    if (response.success) {
      if (selectedFriendId === friend.userId) {
        setSelectedFriendId(null);
      }
      await loadData();
    } else {
      Alert.alert("删除失败", response.error?.message ?? "请稍后重试");
    }
  }

  async function handleSendMessage(type: "text" | "cheer" = "text") {
    if (!selectedFriendId) return;
    const content = type === "cheer" ? "今天也继续加油！" : messageDraft.trim();
    if (!content) return;

    const response = await friendService.sendMessage(selectedFriendId, content, type);
    if (!response.success) {
      Alert.alert("发送失败", response.error?.message ?? "请稍后重试");
      return;
    }
    setMessageDraft("");
    await loadConversation(selectedFriendId);
  }

  return (
    <Screen title="好友">
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
              friends.map((friend) => (
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
                    onPress={() => {
                      setSelectedFriendId(friend.userId);
                      setActiveTab("messages");
                    }}
                  >
                    <Text style={styles.secondaryButtonText}>互动</Text>
                  </TouchableOpacity>
                  <TouchableOpacity style={styles.iconButton} onPress={() => handleRemoveFriend(friend)}>
                    <Ionicons name="trash-outline" size={18} color={colors.error} />
                  </TouchableOpacity>
                </View>
              ))
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
                onPress={() => setActiveBoard(board.key)}
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
                <Text style={styles.summaryHighlight}>
                  你当前第 {activeBoardData.self.rank} 名
                </Text>
                <Text style={styles.summaryText}>
                  共 {activeBoardData.totalParticipants} 人参与
                </Text>
                <Text style={styles.summaryText}>
                  与前一名差距 {formatBoardValue(activeBoardData.metric, activeBoardData.gapToPrevious)} {boardConfigs.find((item) => item.key === activeBoard)?.unit}
                </Text>
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
          <View style={styles.conversationTabs}>
            {conversations.length === 0 ? (
              <EmptyState text="还没有互动消息，先去加好友吧。" />
            ) : (
              conversations.map((item) => (
                <Pressable
                  key={item.friendUserId}
                  style={[styles.conversationChip, selectedFriendId === item.friendUserId && styles.conversationChipActive]}
                  onPress={() => setSelectedFriendId(item.friendUserId)}
                >
                  <Text style={[styles.conversationChipText, selectedFriendId === item.friendUserId && styles.conversationChipTextActive]}>
                    {item.friendDisplayName}
                  </Text>
                  {item.unreadCount > 0 && (
                    <View style={styles.unreadDot}>
                      <Text style={styles.unreadText}>{item.unreadCount}</Text>
                    </View>
                  )}
                </Pressable>
              ))
            )}
          </View>

          <View style={styles.panel}>
            <Text style={styles.panelTitle}>
              {selectedFriend?.displayName ?? selectedConversation?.friendDisplayName ?? "选择一个好友开始互动"}
            </Text>
            <Text style={styles.panelSubtitle}>
              {selectedConversation?.lastMessageAt ? `最近互动：${formatDateTime(selectedConversation.lastMessageAt)}` : "支持发送文字消息和快捷加油"}
            </Text>
          </View>

          <View style={styles.list}>
            {messageLoading ? (
              <ActivityIndicator color={colors.accent} />
            ) : messages.length === 0 ? (
              <EmptyState text="还没有消息，发一句鼓励开始吧。" />
            ) : (
              messages.map((message) => {
                const mine = message.fromUserId === userId;
                return (
                  <View key={message.id} style={[styles.messageBubble, mine ? styles.messageBubbleMine : styles.messageBubbleOther]}>
                    <Text style={[styles.messageText, mine && styles.messageTextMine]}>{message.content}</Text>
                    <Text style={[styles.messageMeta, mine && styles.messageMetaMine]}>
                      {message.type === "cheer" ? "加油" : "消息"} · {formatDateTime(message.createdAt)}
                    </Text>
                  </View>
                );
              })
            )}
          </View>

          <View style={styles.messageComposer}>
            <TextInput
              style={styles.messageInput}
              value={messageDraft}
              onChangeText={setMessageDraft}
              placeholder="发条消息，鼓励一下好友"
              editable={Boolean(selectedFriendId)}
            />
            <TouchableOpacity
              style={[styles.cheerButton, !selectedFriendId && styles.disabledButton]}
              onPress={() => handleSendMessage("cheer")}
              disabled={!selectedFriendId}
            >
              <Ionicons name="sparkles-outline" size={18} color={colors.accent} />
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.primaryIconButton, !selectedFriendId && styles.disabledButton]}
              onPress={() => handleSendMessage("text")}
              disabled={!selectedFriendId}
            >
              <Ionicons name="send-outline" size={18} color={colors.surface} />
            </TouchableOpacity>
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

function formatDateTime(value: string) {
  return value.replace("T", " ").slice(5, 16);
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
  cheerButton: {
    alignItems: "center",
    backgroundColor: "#CCFBF1",
    borderRadius: 14,
    height: 46,
    justifyContent: "center",
    width: 46
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
  conversationChipActive: {
    borderColor: colors.accent
  },
  conversationChipText: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "700"
  },
  conversationChipTextActive: {
    color: colors.accent
  },
  conversationTabs: {
    flexDirection: "row",
    flexWrap: "wrap",
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
  messageBubble: {
    borderRadius: 18,
    maxWidth: "88%",
    paddingHorizontal: 14,
    paddingVertical: 12
  },
  messageBubbleMine: {
    alignSelf: "flex-end",
    backgroundColor: colors.accent
  },
  messageBubbleOther: {
    alignSelf: "flex-start",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1
  },
  messageComposer: {
    alignItems: "center",
    flexDirection: "row",
    gap: 10,
    marginTop: 18
  },
  messageInput: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 14,
    borderWidth: 1,
    color: colors.text,
    flex: 1,
    fontSize: 14,
    height: 46,
    paddingHorizontal: 12
  },
  messageLoading: {
    paddingVertical: 20
  },
  messageMeta: {
    color: colors.muted,
    fontSize: 11,
    marginTop: 6
  },
  messageMetaMine: {
    color: "#CCFBF1"
  },
  messageText: {
    color: colors.text,
    fontSize: 14,
    lineHeight: 20
  },
  messageTextMine: {
    color: colors.surface
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
  panelSubtitle: {
    color: colors.muted,
    fontSize: 13,
    lineHeight: 20
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
    fontSize: 14,
    fontWeight: "800"
  },
  secondaryButton: {
    borderColor: colors.border,
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  secondaryButtonText: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "700"
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
  },
  summaryCard: {
    backgroundColor: "#0F172A",
    borderRadius: 18,
    gap: 8,
    marginTop: 18,
    padding: 18
  },
  summaryHighlight: {
    color: "#F8FAFC",
    fontSize: 24,
    fontWeight: "800"
  },
  summaryText: {
    color: "#CBD5E1",
    fontSize: 13
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
    gap: 10
  },
  unreadDot: {
    alignItems: "center",
    backgroundColor: colors.error,
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
