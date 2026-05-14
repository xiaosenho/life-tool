import { Ionicons } from "@expo/vector-icons";
import { useEffect, useState } from "react";
import {
  Alert,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";

import { Screen } from "@/components/Screen";
import { aiService, ChatMessage, ChatSession, LifeAdvice, MemoryItem } from "@/services/aiService";
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

let cachedSession: ChatSession | null = null;
let cachedMessages: ChatMessage[] = [];

export default function AiScreen() {
  const [advice, setAdvice] = useState<LifeAdvice | null>(null);
  const [session, setSession] = useState<ChatSession | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [memories, setMemories] = useState<MemoryItem[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [activeTools, setActiveTools] = useState<string[]>([]);

  useEffect(() => {
    bootstrap();
  }, []);

  const bootstrap = async () => {
    setLoading(true);
    try {
      const [adviceRes, memoryRes] = await Promise.all([
        aiService.getLifeAdvice(),
        aiService.getMemories()
      ]);

      if (adviceRes.success && adviceRes.data) setAdvice(adviceRes.data);
      if (memoryRes.success && memoryRes.data) setMemories(memoryRes.data.items);

      if (cachedSession) {
        setSession(cachedSession);
        setMessages(cachedMessages);
        const messagesRes = await aiService.getMessages(cachedSession.id);
        if (messagesRes.success && messagesRes.data) {
          cachedMessages = messagesRes.data.messages;
          setMessages(messagesRes.data.messages);
        }
      } else {
        const sessionRes = await aiService.createSession();
        if (sessionRes.success && sessionRes.data) {
          cachedSession = sessionRes.data;
          cachedMessages = [];
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
      cachedMessages = next;
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
          cachedMessages = next;
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

  const refreshAdvice = async () => {
    setLoading(true);
    try {
      const response = await aiService.getLifeAdvice();
      if (response.success && response.data) {
        setAdvice(response.data);
      } else {
        Alert.alert("刷新失败", response.error?.message ?? "请稍后重试。");
      }
    } finally {
      setLoading(false);
    }
  };

  const refreshMemories = async () => {
    const response = await aiService.getMemories();
    if (response.success && response.data) {
      setMemories(response.data.items);
    } else {
      Alert.alert("刷新失败", response.error?.message ?? "请稍后重试。");
    }
  };

  const deleteMemory = async (id: string) => {
    const response = await aiService.deleteMemory(id);
    if (response.success) {
      setMemories((current) => current.filter((item) => item.id !== id));
    } else {
      Alert.alert("删除失败", response.error?.message ?? "请稍后重试。");
    }
  };

  return (
    <Screen title="AI">
      <View style={styles.advicePanel}>
        <View style={styles.panelHeader}>
          <Text style={styles.panelTitle}>近期建议</Text>
          <TouchableOpacity style={styles.smallButton} onPress={refreshAdvice} disabled={loading}>
            <Ionicons name="refresh" size={18} color={colors.accent} />
          </TouchableOpacity>
        </View>
        <Text style={styles.summary}>
          {advice?.summary ?? "正在整理你的近期专注、饮食、记账和提醒数据。"}
        </Text>
        {(advice?.suggestions ?? ["先保持轻量记录，AI 会随着数据增加给出更贴近你的建议。"]).map((item) => (
          <View key={item} style={styles.suggestionRow}>
            <View style={styles.dot} />
            <Text style={styles.suggestionText}>{item}</Text>
          </View>
        ))}
        <Text style={styles.disclaimer}>
          {advice?.disclaimer ?? "AI 建议仅供参考，不构成医疗、营养、财务或法律结论。"}
        </Text>
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>对话</Text>
        <Text style={styles.sectionMeta}>{session ? "长期记忆已启用" : "准备中"}</Text>
      </View>

      <View style={styles.chatBox}>
        {messages.length === 0 ? (
          <Text style={styles.emptyText}>可以问我“结合最近记录，帮我安排明天的专注和饮食”。</Text>
        ) : (
          messages.map((message) => (
            <View
              key={message.id ?? message.messageId}
              style={[styles.messageBubble, message.role === "user" ? styles.userBubble : styles.aiBubble]}
            >
              <Text style={message.role === "user" ? styles.userText : styles.aiText}>{message.content}</Text>
              {message.toolCalls?.map((tool) => (
                <Text key={tool.toolName} style={styles.toolText}>
                  {toolLabels[tool.toolName] ?? tool.toolName}：{tool.status === "succeeded" ? "完成" : tool.status}
                </Text>
              ))}
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

      <View style={styles.inputRow}>
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

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>长期记忆</Text>
        <TouchableOpacity style={styles.memoryRefresh} onPress={refreshMemories}>
          <Text style={styles.sectionMeta}>{memories.length} 条</Text>
          <Ionicons name="refresh" size={15} color={colors.muted} />
        </TouchableOpacity>
      </View>
      <View style={styles.memoryList}>
        {memories.length === 0 ? (
          <Text style={styles.emptyText}>还没有长期记忆。</Text>
        ) : (
          memories.map((memory) => (
            <View key={memory.id} style={styles.memoryItem}>
              <View style={styles.memoryContent}>
                <Text style={styles.memoryType}>{memory.type}</Text>
                <Text style={styles.memoryText}>{memory.content}</Text>
              </View>
              <TouchableOpacity style={styles.iconButton} onPress={() => deleteMemory(memory.id)}>
                <Ionicons name="trash-outline" size={18} color={colors.error} />
              </TouchableOpacity>
            </View>
          ))
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  advicePanel: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    gap: 10,
    padding: 16
  },
  aiBubble: {
    alignSelf: "flex-start",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1
  },
  aiText: {
    color: colors.text,
    fontSize: 14,
    lineHeight: 20
  },
  chatBox: {
    gap: 10,
    marginBottom: 12
  },
  disabledButton: {
    opacity: 0.55
  },
  disclaimer: {
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18
  },
  dot: {
    backgroundColor: colors.accent,
    borderRadius: 3,
    height: 6,
    marginTop: 7,
    width: 6
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 20
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
    maxHeight: 96,
    minHeight: 44,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  inputRow: {
    alignItems: "flex-end",
    flexDirection: "row",
    gap: 10,
    marginBottom: 22
  },
  memoryContent: {
    flex: 1,
    gap: 3
  },
  memoryItem: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: "row",
    gap: 10,
    padding: 12
  },
  memoryList: {
    gap: 10
  },
  memoryRefresh: {
    alignItems: "center",
    flexDirection: "row",
    gap: 5
  },
  memoryText: {
    color: colors.text,
    fontSize: 14,
    lineHeight: 20
  },
  memoryType: {
    color: colors.muted,
    fontSize: 12
  },
  messageBubble: {
    borderRadius: 12,
    maxWidth: "88%",
    padding: 12
  },
  panelHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between"
  },
  panelTitle: {
    color: colors.text,
    fontSize: 17,
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
  sendButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 12,
    height: 44,
    justifyContent: "center",
    width: 44
  },
  smallButton: {
    alignItems: "center",
    height: 34,
    justifyContent: "center",
    width: 34
  },
  suggestionRow: {
    flexDirection: "row",
    gap: 8
  },
  suggestionText: {
    color: colors.text,
    flex: 1,
    fontSize: 14,
    lineHeight: 20
  },
  summary: {
    color: colors.text,
    fontSize: 15,
    lineHeight: 22
  },
  toolText: {
    color: colors.muted,
    fontSize: 12,
    marginTop: 8
  },
  toolPanel: {
    alignSelf: "flex-start",
    backgroundColor: "#F1F5F9",
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    gap: 2,
    maxWidth: "88%",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  userBubble: {
    alignSelf: "flex-end",
    backgroundColor: colors.accent
  },
  userText: {
    color: colors.surface,
    fontSize: 14,
    lineHeight: 20
  }
});
