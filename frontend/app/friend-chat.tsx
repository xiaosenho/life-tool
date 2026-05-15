import { Ionicons } from "@expo/vector-icons";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  RefreshControl,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";
import { Stack, useLocalSearchParams, useRouter } from "expo-router";

import { Screen } from "@/components/Screen";
import { FriendMessage, friendService } from "@/services/friendService";
import { useAuthStore } from "@/store/authStore";
import { colors } from "@/theme/colors";
import { formatDateTimeCn } from "@/utils/time";

export default function FriendChatScreen() {
  const router = useRouter();
  const { friendUserId, friendName } = useLocalSearchParams<{ friendUserId: string; friendName?: string }>();
  const userId = useAuthStore((state) => state.user?.id ?? "");
  const [messages, setMessages] = useState<FriendMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [sending, setSending] = useState(false);

  const title = useMemo(() => friendName || "聊天", [friendName]);

  const loadMessages = useCallback(async (silent = false) => {
    if (!friendUserId) {
      return;
    }
    if (!silent) {
      setLoading(true);
    }
    try {
      const response = await friendService.listMessages(friendUserId);
      if (response.success && response.data) {
        setMessages(response.data);
        await friendService.markConversationRead(friendUserId);
      } else {
        setMessages([]);
      }
    } catch (error) {
      Alert.alert("加载失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [friendUserId]);

  useEffect(() => {
    void loadMessages();
  }, [loadMessages]);

  async function onRefresh() {
    setRefreshing(true);
    await loadMessages(true);
  }

  async function sendMessage(type: "text" | "cheer" = "text") {
    if (!friendUserId || sending) return;
    const content = type === "cheer" ? "今天也继续加油！" : draft.trim();
    if (!content) return;

    setSending(true);
    try {
      const response = await friendService.sendMessage(friendUserId, content, type);
      if (!response.success) {
        Alert.alert("发送失败", response.error?.message ?? "请稍后重试");
        return;
      }
      setDraft("");
      await loadMessages(true);
    } finally {
      setSending(false);
    }
  }

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          headerTitle: title,
          headerLeft: () => (
            <TouchableOpacity onPress={() => router.back()} style={{ marginRight: 8 }}>
              <Ionicons name="chevron-back" size={22} color={colors.text} />
            </TouchableOpacity>
          )
        }}
      />
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={Platform.OS === "ios" ? 88 : 0}
      >
        <Screen scrollable={false} style={styles.screen} contentContainerStyle={styles.screenContent}>
          {loading ? (
            <View style={styles.centerBlock}>
              <ActivityIndicator color={colors.accent} />
            </View>
          ) : (
            <FlatList
              data={messages}
              keyExtractor={(item) => item.id}
              renderItem={({ item }) => {
                const mine = item.fromUserId === userId;
                return (
                  <View style={[styles.messageBubble, mine ? styles.messageBubbleMine : styles.messageBubbleOther]}>
                    <Text style={[styles.messageText, mine && styles.messageTextMine]}>{item.content}</Text>
                    <Text style={[styles.messageMeta, mine && styles.messageMetaMine]}>
                      {item.type === "cheer" ? "加油" : "消息"} · {formatDateTimeCn(item.createdAt)}
                    </Text>
                  </View>
                );
              }}
              contentContainerStyle={styles.messageList}
              refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.accent} />}
              ListEmptyComponent={<Text style={styles.emptyText}>还没有消息，发一句鼓励开始吧。</Text>}
            />
          )}
        </Screen>

        <View style={styles.composer}>
          <TextInput
            style={styles.input}
            value={draft}
            onChangeText={setDraft}
            placeholder="发条消息，鼓励一下好友"
            placeholderTextColor={colors.muted}
          />
          <TouchableOpacity
            style={[styles.cheerButton, sending && styles.disabledButton]}
            onPress={() => sendMessage("cheer")}
            disabled={sending}
          >
            <Ionicons name="sparkles-outline" size={18} color={colors.accent} />
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.sendButton, sending && styles.disabledButton]}
            onPress={() => sendMessage("text")}
            disabled={sending}
          >
            <Ionicons name="send-outline" size={18} color={colors.surface} />
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </>
  );
}

const styles = StyleSheet.create({
  centerBlock: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center"
  },
  cheerButton: {
    alignItems: "center",
    backgroundColor: "#CCFBF1",
    borderRadius: 14,
    height: 46,
    justifyContent: "center",
    width: 46
  },
  composer: {
    alignItems: "center",
    backgroundColor: colors.background,
    borderTopColor: colors.border,
    borderTopWidth: 1,
    flexDirection: "row",
    gap: 10,
    paddingBottom: 16,
    paddingHorizontal: 18,
    paddingTop: 12
  },
  container: {
    backgroundColor: colors.background,
    flex: 1
  },
  disabledButton: {
    opacity: 0.5
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 21,
    paddingVertical: 20,
    textAlign: "center"
  },
  input: {
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
  messageList: {
    flexGrow: 1,
    gap: 10,
    paddingBottom: 18
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
  screen: {
    flex: 1
  },
  screenContent: {
    flex: 1,
    paddingBottom: 0
  },
  sendButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 14,
    height: 46,
    justifyContent: "center",
    width: 46
  }
});

