import { Ionicons } from "@expo/vector-icons";
import { useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";

import { Screen } from "@/components/Screen";
import { aiService, ChatMessage, ChatSession } from "@/services/aiService";
import { cachedAiMessages, cachedAiSession, setCachedAiMessages, setCachedAiSession } from "@/features/ai/aiChatCache";
import { colors } from "@/theme/colors";

const toolLabels: Record<string, string> = {
  get_focus_summary: "正在读取专注汇总",
  get_habit_summary: "正在读取习惯汇总",
  get_diet_summary: "正在读取饮食汇总",
  get_ledger_summary: "正在读取记账汇总",
  get_upcoming_events: "正在读取重要事件",
  get_user_profile_context: "正在读取偏好设置"
};

const defaultEnabledTools = [
  "get_focus_summary",
  "get_habit_summary",
  "get_diet_summary",
  "get_ledger_summary",
  "get_upcoming_events",
  "get_user_profile_context"
];

export default function AiChatScreen() {
  const [session, setSession] = useState<ChatSession | null>(cachedAiSession);
  const [messages, setMessages] = useState<ChatMessage[]>(cachedAiMessages);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [activeTools, setActiveTools] = useState<string[]>([]);

  useEffect(() => {
    void bootstrap();
  }, []);

  const bootstrap = async () => {
    setLoading(true);
    try {
      if (cachedAiSession) {
        setSession(cachedAiSession);
        setMessages(cachedAiMessages);
        const messagesRes = await aiService.getMessages(cachedAiSession.id);
        if (messagesRes.success && messagesRes.data) {
          setCachedAiMessages(messagesRes.data.messages);
          setMessages(messagesRes.data.messages);
        }
      } else {
        const sessionRes = await aiService.createSession();
        if (sessionRes.success && sessionRes.data) {
          setCachedAiSession(sessionRes.data);
          setCachedAiMessages([]);
          setSession(sessionRes.data);
          setMessages([]);
        }
      }
    } catch (error) {
      Alert.alert("加载失败", error instanceof Error ? error.message : "请稍后重试。");
    } finally {
      setLoading(false);
    }
  };

  const sendMessage = async () => {
    const content = input.trim();
    if (!content || !session || loading || sending) return;

    const optimistic: ChatMessage = {
      id: `local-${Date.now()}`,
      role: "user",
      content,
      createdAt: new Date().toISOString()
    };

    setMessages((current) => {
      const next = [...current, optimistic];
      setCachedAiMessages(next);
      return next;
    });
    setInput("");
    setSending(true);
    setActiveTools(defaultEnabledTools);
    try {
      const response = await aiService.sendMessage(session.id, content, defaultEnabledTools);
      if (response.success && response.data) {
        setMessages((current) => {
          const next = [...current, response.data as ChatMessage];
          setCachedAiMessages(next);
          return next;
        });
      } else {
        Alert.alert("发送失败", response.error?.message ?? "请稍后重试。");
      }
    } catch (error) {
      Alert.alert("发送失败", error instanceof Error ? error.message : "请稍后重试。");
    } finally {
      setActiveTools([]);
      setSending(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
      keyboardVerticalOffset={Platform.OS === "ios" ? 88 : 0}
    >
      <Screen title="AI 对话" scrollable={false} style={styles.screen} contentContainerStyle={styles.screenContent}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>对话</Text>
          <Text style={styles.sectionMeta}>{session ? "长期记忆已启用" : "准备中"}</Text>
        </View>

        <View style={styles.chatBox}>
          {loading ? (
            <View style={styles.loadingBlock}>
              <ActivityIndicator size="small" color={colors.accent} />
              <Text style={styles.loadingText}>正在准备对话...</Text>
            </View>
          ) : messages.length === 0 ? (
            <Text style={styles.emptyText}>可以问我“结合最近记录，帮我安排明天的专注和饮食”。</Text>
          ) : (
            messages.map((message) => (
              <View
                key={message.id ?? message.messageId}
                style={styles.messageRow}
              >
                <View
                  style={[styles.messageBubble, message.role === "user" ? styles.userBubble : styles.aiBubble]}
                >
                  <Text style={message.role === "user" ? styles.userText : styles.aiText}>{message.content}</Text>
                </View>
                {message.role === "assistant" && message.longTermMemorySaved ? (
                  <View style={styles.memoryHint}>
                    <Ionicons name="sparkles-outline" size={14} color={colors.accent} />
                    <Text style={styles.memoryHintText}>已记住你的长期偏好</Text>
                  </View>
                ) : null}
              </View>
            ))
          )}
          {activeTools.length > 0 && (
            <View style={styles.toolPanel}>
              {activeTools.map((tool) => (
                <Text key={tool} style={styles.toolText}>{toolLabels[tool] ?? tool}</Text>
              ))}
            </View>
          )}
        </View>
      </Screen>

      <View style={styles.composer}>
        <TextInput
          style={styles.input}
          value={input}
          onChangeText={setInput}
          placeholder="输入你的问题..."
          multiline
        />
        <TouchableOpacity style={[styles.sendButton, sending && styles.disabledButton]} onPress={sendMessage}>
          <Ionicons name="send" size={18} color={colors.surface} />
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  aiBubble: {
    alignSelf: "flex-start",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1
  },
  aiText: {
    color: colors.text,
    fontSize: 14,
    lineHeight: 22
  },
  chatBox: {
    flex: 1,
    gap: 10,
    marginTop: 12
  },
  composer: {
    alignItems: "flex-end",
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
    lineHeight: 22
  },
  input: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    color: colors.text,
    flex: 1,
    fontSize: 14,
    maxHeight: 120,
    minHeight: 48,
    paddingHorizontal: 12,
    paddingVertical: 12
  },
  loadingBlock: {
    alignItems: "center",
    gap: 10,
    paddingVertical: 20
  },
  loadingText: {
    color: colors.muted,
    fontSize: 13
  },
  messageBubble: {
    borderRadius: 14,
    maxWidth: "92%",
    paddingHorizontal: 14,
    paddingVertical: 12
  },
  memoryHint: {
    alignItems: "center",
    flexDirection: "row",
    gap: 6,
    marginTop: 6,
    paddingHorizontal: 4
  },
  memoryHintText: {
    color: colors.accent,
    fontSize: 12,
    fontWeight: "700"
  },
  messageRow: {
    alignItems: "flex-start"
  },
  sectionHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between"
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
    borderRadius: 12,
    height: 48,
    justifyContent: "center",
    width: 48
  },
  toolPanel: {
    backgroundColor: "#F8FAFC",
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    gap: 6,
    padding: 12
  },
  toolText: {
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18
  },
  userBubble: {
    alignSelf: "flex-end",
    backgroundColor: colors.accent
  },
  userText: {
    color: colors.surface,
    fontSize: 14,
    lineHeight: 22
  }
});
