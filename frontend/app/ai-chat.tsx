import React, { useState, useCallback } from "react";
import {
  ActivityIndicator,
  Alert,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useFocusEffect } from "expo-router";
import { Ionicons } from "@expo/vector-icons";

import { Screen } from "@/components/Screen";
import { colors } from "@/theme/colors";
import { aiService, ChatSession, ChatMessage, LifeAdviceResponse } from "@/services/aiService";

export default function AiChatScreen() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [showSessions, setShowSessions] = useState(true);
  const [advice, setAdvice] = useState<LifeAdviceResponse | null>(null);
  const [loadingAdvice, setLoadingAdvice] = useState(false);

  const loadSessions = useCallback(async () => {
    try {
      const data = await aiService.listChatSessions();
      setSessions(data);
    } catch {
      // ignore
    }
  }, []);

  useFocusEffect(useCallback(() => { loadSessions(); }, [loadSessions]));

  const handleNewSession = async () => {
    try {
      const session = await aiService.createChatSession();
      setSessions((prev) => [session, ...prev]);
      setActiveSessionId(session.id);
      setMessages([]);
      setShowSessions(false);
    } catch {
      Alert.alert("错误", "创建对话失败");
    }
  };

  const handleSelectSession = async (sessionId: string) => {
    setActiveSessionId(sessionId);
    try {
      const msgs = await aiService.listChatMessages(sessionId);
      setMessages(msgs);
    } catch {
      setMessages([]);
    }
    setShowSessions(false);
  };

  const handleSend = async () => {
    if (!input.trim() || !activeSessionId) return;
    const userContent = input.trim();
    setInput("");
    setMessages((prev) => [...prev, { id: `temp_${Date.now()}`, sessionId: activeSessionId, role: "user", content: userContent, createdAt: new Date().toISOString() }]);
    setSending(true);
    try {
      const reply = await aiService.sendChatMessage(activeSessionId, userContent);
      setMessages((prev) => [...prev, reply]);
    } catch {
      Alert.alert("错误", "发送消息失败");
    } finally {
      setSending(false);
    }
  };

  const handleGetAdvice = async () => {
    setLoadingAdvice(true);
    try {
      const result = await aiService.getLifeAdvice();
      setAdvice(result);
    } catch {
      Alert.alert("错误", "获取建议失败");
    } finally {
      setLoadingAdvice(false);
    }
  };

  const handleDeleteSession = async (sessionId: string) => {
    try {
      await aiService.deleteChatSession(sessionId);
      setSessions((prev) => prev.filter((s) => s.id !== sessionId));
      if (activeSessionId === sessionId) {
        setActiveSessionId(null);
        setMessages([]);
        setShowSessions(true);
      }
    } catch {
      Alert.alert("错误", "删除失败");
    }
  };

  if (showSessions) {
    return (
      <Screen title="AI 助手">
        <View style={styles.adviceSection}>
          {loadingAdvice ? (
            <ActivityIndicator color={colors.accent} />
          ) : advice ? (
            <View style={styles.adviceCard}>
              <Text style={styles.adviceSummary}>{advice.summary}</Text>
              {advice.suggestions.map((s, i) => (
                <Text key={i} style={styles.adviceItem}>• {s}</Text>
              ))}
              <Text style={styles.adviceDisclaimer}>{advice.disclaimer}</Text>
              <TouchableOpacity onPress={handleGetAdvice}>
                <Text style={styles.adviceRefresh}>刷新建议</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <TouchableOpacity style={styles.adviceTrigger} onPress={handleGetAdvice}>
              <Ionicons name="bulb-outline" size={24} color={colors.accent} />
              <Text style={styles.adviceTriggerText}>获取生活建议</Text>
            </TouchableOpacity>
          )}
        </View>

        <View style={styles.sessionHeader}>
          <Text style={styles.sessionTitle}>历史对话</Text>
          <TouchableOpacity style={styles.newChatBtn} onPress={handleNewSession}>
            <Ionicons name="add-circle" size={22} color={colors.accent} />
            <Text style={styles.newChatBtnText}>新对话</Text>
          </TouchableOpacity>
        </View>

        {sessions.length === 0 ? (
          <Text style={styles.emptyText}>还没有对话记录，开始一个新对话吧</Text>
        ) : (
          sessions.map((s) => (
            <TouchableOpacity key={s.id} style={styles.sessionItem} onPress={() => handleSelectSession(s.id)}>
              <Ionicons name="chatbubble-outline" size={20} color={colors.muted} />
              <Text style={styles.sessionName}>{s.title}</Text>
              <TouchableOpacity onPress={() => handleDeleteSession(s.id)}>
                <Ionicons name="trash-outline" size={18} color={colors.error} />
              </TouchableOpacity>
            </TouchableOpacity>
          ))
        )}
      </Screen>
    );
  }

  return (
    <Screen title="AI 助手" scrollable={false}>
      <View style={styles.chatHeader}>
        <TouchableOpacity onPress={() => setShowSessions(true)}>
          <Ionicons name="arrow-back" size={24} color={colors.text} />
        </TouchableOpacity>
        <Text style={styles.chatHeaderTitle}>
          {sessions.find((s) => s.id === activeSessionId)?.title ?? "对话"}
        </Text>
        <View style={{ width: 24 }} />
      </View>

      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={100}
      >
        <FlatList
          data={messages}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.messageList}
          renderItem={({ item }) => (
            <View style={[styles.messageRow, item.role === "user" ? styles.userRow : styles.assistantRow]}>
              <View style={[styles.messageBubble, item.role === "user" ? styles.userBubble : styles.assistantBubble]}>
                <Text style={[styles.messageText, item.role === "user" && styles.userMessageText]}>
                  {item.content}
                </Text>
              </View>
            </View>
          )}
        />

        <View style={styles.inputRow}>
          <TextInput
            style={styles.chatInput}
            placeholder="输入消息..."
            value={input}
            onChangeText={setInput}
            multiline
          />
          <TouchableOpacity
            style={[styles.sendButton, (!input.trim() || sending) && styles.sendButtonDisabled]}
            onPress={handleSend}
            disabled={!input.trim() || sending}
          >
            {sending ? (
              <ActivityIndicator size="small" color="#FFF" />
            ) : (
              <Ionicons name="send" size={20} color="#FFF" />
            )}
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  adviceCard: {
    backgroundColor: `${colors.accent}08`,
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: `${colors.accent}20`,
  },
  adviceDisclaimer: {
    color: colors.muted,
    fontSize: 11,
    fontStyle: "italic",
    marginTop: 8,
  },
  adviceItem: {
    color: colors.text,
    fontSize: 14,
    lineHeight: 22,
    marginTop: 4,
  },
  adviceRefresh: {
    color: colors.accent,
    fontSize: 13,
    fontWeight: "600",
    marginTop: 10,
  },
  adviceSection: {
    marginBottom: 24,
  },
  adviceSummary: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "600",
    marginBottom: 8,
  },
  adviceTrigger: {
    alignItems: "center",
    backgroundColor: `${colors.accent}08`,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: `${colors.accent}20`,
    flexDirection: "row",
    gap: 8,
    justifyContent: "center",
    padding: 16,
  },
  adviceTriggerText: {
    color: colors.accent,
    fontSize: 16,
    fontWeight: "600",
  },
  assistantBubble: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1,
  },
  assistantRow: {
    justifyContent: "flex-start",
  },
  chatHeader: {
    alignItems: "center",
    borderBottomWidth: 1,
    borderColor: colors.border,
    flexDirection: "row",
    justifyContent: "space-between",
    paddingBottom: 12,
  },
  chatHeaderTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700",
  },
  chatInput: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 20,
    borderWidth: 1,
    color: colors.text,
    flex: 1,
    maxHeight: 80,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
    marginTop: 40,
    textAlign: "center",
  },
  inputRow: {
    alignItems: "flex-end",
    flexDirection: "row",
    gap: 8,
    paddingTop: 8,
  },
  messageBubble: {
    borderRadius: 16,
    maxWidth: "80%",
    padding: 14,
  },
  messageList: {
    paddingBottom: 8,
  },
  messageRow: {
    flexDirection: "row",
    marginBottom: 12,
  },
  messageText: {
    color: colors.text,
    fontSize: 15,
    lineHeight: 22,
  },
  newChatBtn: {
    alignItems: "center",
    flexDirection: "row",
    gap: 4,
  },
  newChatBtnText: {
    color: colors.accent,
    fontSize: 14,
    fontWeight: "600",
  },
  sendButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 24,
    height: 44,
    justifyContent: "center",
    width: 44,
  },
  sendButtonDisabled: {
    opacity: 0.5,
  },
  sessionHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 12,
  },
  sessionItem: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 10,
    borderWidth: 1,
    flexDirection: "row",
    gap: 10,
    marginBottom: 8,
    padding: 14,
  },
  sessionName: {
    color: colors.text,
    flex: 1,
    fontSize: 15,
    fontWeight: "500",
  },
  sessionTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700",
  },
  userBubble: {
    backgroundColor: colors.accent,
  },
  userMessageText: {
    color: "#FFF",
  },
  userRow: {
    justifyContent: "flex-end",
  },
});
