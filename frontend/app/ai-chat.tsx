import { Ionicons } from "@expo/vector-icons";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  PanResponder,
  ActivityIndicator,
  Alert,
  Image,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  GestureResponderEvent,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";
import { useHeaderHeight } from "@react-navigation/elements";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Audio } from "expo-av";

import { Screen } from "@/components/Screen";
import { aiService, ChatMessage, ChatSession } from "@/services/aiService";
import {
  pickChatImage,
  requestAudioPermission,
  startAudioRecording,
  stopAudioRecording,
  cancelAudioRecording,
  toAttachmentPayload,
  uploadChatAudio,
  uploadChatImage
} from "@/services/chatMediaService";
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

function shouldRenderMessageText(content: string | null | undefined) {
  const normalized = content?.trim();
  return !!normalized && normalized !== "[语音消息]" && normalized !== "[图片消息]";
}

export default function AiChatScreen() {
  const [session, setSession] = useState<ChatSession | null>(cachedAiSession);
  const [messages, setMessages] = useState<ChatMessage[]>(cachedAiMessages);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [uploadingMedia, setUploadingMedia] = useState(false);
  const [recording, setRecording] = useState<Audio.Recording | null>(null);
  const [playingAudioId, setPlayingAudioId] = useState<string | null>(null);
  const [audioProgress, setAudioProgress] = useState<Record<string, number>>({});
  const [previewImageUrl, setPreviewImageUrl] = useState<string | null>(null);
  const [activeTools, setActiveTools] = useState<string[]>([]);
  const [recordGestureActive, setRecordGestureActive] = useState(false);
  const [recordWillCancel, setRecordWillCancel] = useState(false);
  const scrollViewRef = useRef<ScrollView | null>(null);
  const recordFinalizingRef = useRef(false);
  const recordStartingRef = useRef(false);
  const hasInitialScrolledRef = useRef(false);
  const soundRef = useRef<Audio.Sound | null>(null);
  const headerHeight = useHeaderHeight();
  const insets = useSafeAreaInsets();

  const scrollToBottom = useCallback((animated = true) => {
    requestAnimationFrame(() => {
      scrollViewRef.current?.scrollToEnd({ animated });
    });
  }, []);

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
          if (!hasInitialScrolledRef.current) {
            hasInitialScrolledRef.current = true;
            setTimeout(() => scrollToBottom(false), 0);
          }
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
    scrollToBottom(true);
    try {
      const response = await aiService.sendMessage(session.id, content, defaultEnabledTools);
      if (response.success && response.data) {
        setMessages((current) => {
          const next = [...current, response.data as ChatMessage];
          setCachedAiMessages(next);
          return next;
        });
        scrollToBottom(true);
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

  const sendImage = async () => {
    if (!session || loading || sending || uploadingMedia) return;
    try {
      const picked = await pickChatImage();
      if (!picked) {
        return;
      }
      setUploadingMedia(true);
      const uploaded = await uploadChatImage({
        uri: picked.uri,
        width: picked.width,
        height: picked.height,
        fileSize: picked.fileSize,
        mimeType: picked.mimeType
      });

      const optimistic: ChatMessage = {
        id: `local-image-${Date.now()}`,
        role: "user",
        content: "[图片消息]",
        attachment: {
          assetId: uploaded.assetId,
          kind: "image",
          contentType: uploaded.contentType,
          url: uploaded.url || "",
          width: uploaded.width,
          height: uploaded.height
        },
        createdAt: new Date().toISOString()
      };

      setMessages((current) => {
        const next = [...current, optimistic];
        setCachedAiMessages(next);
        return next;
      });
      setSending(true);
      setActiveTools(defaultEnabledTools);
      scrollToBottom(true);

      const response = await aiService.sendMessage(session.id, "", defaultEnabledTools, toAttachmentPayload(uploaded));
      if (response.success && response.data) {
        setMessages((current) => {
          const next = [...current, response.data as ChatMessage];
          setCachedAiMessages(next);
          return next;
        });
        scrollToBottom(true);
      } else {
        Alert.alert("发送失败", response.error?.message ?? "请稍后重试。");
      }
    } catch (error) {
      Alert.alert("发送失败", error instanceof Error ? error.message : "请稍后重试。");
    } finally {
      setActiveTools([]);
      setUploadingMedia(false);
      setSending(false);
    }
  };

  const toggleRecordAudio = async () => {
    if (!session || loading || sending || uploadingMedia || recording || recordStartingRef.current) return;
    recordStartingRef.current = true;
    try {
      const granted = await requestAudioPermission();
      if (!granted) {
        setRecordGestureActive(false);
        Alert.alert("需要权限", "请先允许麦克风权限");
        return;
      }
      const nextRecording = await startAudioRecording();
      setRecording(nextRecording);
    } catch (error) {
      setRecordGestureActive(false);
      Alert.alert("录音失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      recordStartingRef.current = false;
    }
  };

  const finishRecordAudio = async (currentRecording: Audio.Recording) => {
    if (!session) return;
    try {
      setUploadingMedia(true);
      const result = await stopAudioRecording(currentRecording);
      const uploaded = await uploadChatAudio({
        uri: result.uri,
        fileSize: result.fileSize,
        durationSeconds: result.durationSeconds,
        mimeType: Platform.OS === "ios" ? "audio/m4a" : "audio/mp4"
      });

      const optimistic: ChatMessage = {
        id: `local-audio-${Date.now()}`,
        role: "user",
        content: "[语音消息]",
        attachment: {
          assetId: uploaded.assetId,
          kind: "audio",
          contentType: uploaded.contentType,
          url: uploaded.url || "",
          durationSeconds: uploaded.durationSeconds
        },
        createdAt: new Date().toISOString()
      };

      setMessages((current) => {
        const next = [...current, optimistic];
        setCachedAiMessages(next);
        return next;
      });
      setSending(true);
      setActiveTools(defaultEnabledTools);
      scrollToBottom(true);
      const response = await aiService.sendMessage(session.id, "", defaultEnabledTools, toAttachmentPayload(uploaded));
      if (response.success && response.data) {
        setMessages((current) => {
          const next = [...current, response.data as ChatMessage];
          setCachedAiMessages(next);
          return next;
        });
        scrollToBottom(true);
      } else {
        Alert.alert("发送失败", response.error?.message ?? "请稍后重试。");
      }
    } catch (error) {
      Alert.alert("语音发送失败", error instanceof Error ? error.message : "请稍后重试。");
    } finally {
      setActiveTools([]);
      setUploadingMedia(false);
      setSending(false);
    }
  };

  const finalizeRecord = async (send: boolean) => {
    if (recordStartingRef.current) {
      setTimeout(() => {
        void finalizeRecord(send);
      }, 80);
      return;
    }
    if (!recording || recordFinalizingRef.current) {
      setRecordGestureActive(false);
      setRecordWillCancel(false);
      return;
    }
    recordFinalizingRef.current = true;
    const currentRecording = recording;
    setRecording(null);
    try {
      if (send) {
        await finishRecordAudio(currentRecording);
      } else {
        await cancelAudioRecording(currentRecording);
      }
    } finally {
      setRecordGestureActive(false);
      setRecordWillCancel(false);
      recordFinalizingRef.current = false;
    }
  };

  const handleRecordPressOut = async (_: GestureResponderEvent) => {
    if (!recording) return;
    await finalizeRecord(!recordWillCancel);
  };

  const recordPanResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => false,
      onMoveShouldSetPanResponder: () => !!recordGestureActive,
      onPanResponderMove: (_, gestureState) => {
        if (!recordGestureActive) return;
        setRecordWillCancel(gestureState.dy < -50);
      },
      onPanResponderRelease: async () => {
        if (!recording) {
          setRecordGestureActive(false);
          setRecordWillCancel(false);
          return;
        }
        await finalizeRecord(!recordWillCancel);
      },
      onPanResponderTerminate: async () => {
        if (recording) {
          await finalizeRecord(false);
        }
      }
    })
  ).current;

  const playAudio = async (message: ChatMessage) => {
    if (!message.attachment?.url) {
      return;
    }
    try {
      if (soundRef.current) {
        await soundRef.current.unloadAsync();
        soundRef.current = null;
      }
      const currentId = message.id ?? message.messageId ?? null;
      setPlayingAudioId(currentId);
      const { sound } = await Audio.Sound.createAsync(
        { uri: message.attachment.url },
        { shouldPlay: true },
        (status) => {
          if (status.isLoaded && currentId) {
            const duration = status.durationMillis ?? 1;
            const position = status.positionMillis ?? 0;
            setAudioProgress((current) => ({ ...current, [currentId]: Math.max(0, Math.min(1, position / duration)) }));
          }
          if (!status.isLoaded || !status.didJustFinish) {
            return;
          }
          setPlayingAudioId(null);
          if (currentId) {
            setAudioProgress((current) => ({ ...current, [currentId]: 0 }));
          }
        }
      );
      soundRef.current = sound;
    } catch (error) {
      setPlayingAudioId(null);
      Alert.alert("播放失败", error instanceof Error ? error.message : "请稍后重试");
    }
  };

  return (
    <>
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        keyboardVerticalOffset={Platform.OS === "ios" ? headerHeight : 0}
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
            <Text style={styles.emptyText}>可以直接问我今天的安排。</Text>
          ) : (
            <ScrollView
              ref={scrollViewRef}
              contentContainerStyle={[
                styles.messageList,
                { paddingBottom: Math.max(insets.bottom + 24, 36) }
              ]}
              keyboardShouldPersistTaps="handled"
            >
              {messages.map((message) => (
                <View style={styles.messageRow}>
                  <View
                    style={[styles.messageBubble, message.role === "user" ? styles.userBubble : styles.aiBubble]}
                  >
                    {message.attachment?.kind === "image" && message.attachment.url ? (
                      <TouchableOpacity activeOpacity={0.9} onPress={() => setPreviewImageUrl(message.attachment?.url ?? null)}>
                        <Image source={{ uri: message.attachment.url }} style={styles.messageImage} resizeMode="cover" />
                      </TouchableOpacity>
                    ) : null}
                    {message.attachment?.kind === "audio" ? (
                      <TouchableOpacity
                        style={[styles.audioBubble, message.role === "user" && styles.audioBubbleMine]}
                        onPress={() => playAudio(message)}
                      >
                        <Ionicons
                          name={playingAudioId === (message.id ?? message.messageId ?? null) ? "pause-circle-outline" : "play-circle-outline"}
                          size={20}
                          color={message.role === "user" ? colors.surface : colors.accent}
                        />
                        <View style={styles.audioInfo}>
                          <Text style={message.role === "user" ? styles.audioTextMine : styles.audioText}>
                            {message.attachment.durationSeconds ? `${message.attachment.durationSeconds}s 语音` : "语音消息"}
                          </Text>
                          <View style={[styles.waveTrack, message.role === "user" && styles.waveTrackMine]}>
                            {Array.from({ length: 16 }).map((_, index) => {
                              const currentId = message.id ?? message.messageId ?? "";
                              const activeCount = Math.round((audioProgress[currentId] ?? 0) * 16);
                              return (
                                <View
                                  key={`${currentId}-wave-${index}`}
                                  style={[
                                    styles.waveBar,
                                    message.role === "user" && styles.waveBarMine,
                                    index < activeCount && (message.role === "user" ? styles.waveBarActiveMine : styles.waveBarActive)
                                  ]}
                                />
                              );
                            })}
                          </View>
                        </View>
                      </TouchableOpacity>
                    ) : null}
                    {shouldRenderMessageText(message.content) ? (
                      <Text style={message.role === "user" ? styles.userText : styles.aiText}>{message.content}</Text>
                    ) : null}
                  </View>
                  {message.role === "assistant" && message.longTermMemorySaved ? (
                    <View style={styles.memoryHint}>
                      <Ionicons name="sparkles-outline" size={14} color={colors.accent} />
                      <Text style={styles.memoryHintText}>已记住你的长期偏好</Text>
                    </View>
                  ) : null}
                </View>
              ))}
              <View style={styles.bottomAnchor} />
            </ScrollView>
          )}
          {activeTools.length > 0 && (
            <View style={styles.toolPanel}>
              {activeTools.map((tool) => (
                <Text key={tool} style={styles.toolText} numberOfLines={1} ellipsizeMode="tail">
                  {toolLabels[tool] ?? tool}
                </Text>
              ))}
            </View>
          )}
        </View>
      </Screen>

      {recordGestureActive ? (
        <View style={styles.recordHintOverlay} pointerEvents="none">
          <View style={[styles.recordHintCard, recordWillCancel && styles.recordHintCardCancel]}>
            <Ionicons
              name={recordWillCancel ? "close-circle" : "arrow-up-circle"}
              size={18}
              color={colors.surface}
            />
            <Text style={styles.recordHintTitle}>
              {recordWillCancel ? "松开取消发送" : "上滑取消发送"}
            </Text>
            <Text style={styles.recordHintSubtitle}>
              {recordWillCancel ? "当前松手将不会发送语音" : "继续按住，向上滑动可取消"}
            </Text>
          </View>
        </View>
      ) : null}

      <View style={[styles.composer, { paddingBottom: Math.max(insets.bottom, 12) }]}>
        <Pressable
          style={({ pressed }) => [
            styles.recordButton,
            recordGestureActive && styles.recordButtonExpanded,
            recording && styles.recordButtonActive,
            pressed && !recording && styles.recordButtonPressed,
            (sending || uploadingMedia) && styles.disabledButton
          ]}
          onLongPress={async () => {
            setRecordGestureActive(true);
            setRecordWillCancel(false);
            await toggleRecordAudio();
          }}
          onPressOut={handleRecordPressOut}
          delayLongPress={180}
          disabled={sending || uploadingMedia}
          {...recordPanResponder.panHandlers}
        >
          <Ionicons
            name={recording ? "radio-button-on" : "mic-outline"}
            size={18}
            color={recording ? colors.surface : colors.accent}
          />
          {recordGestureActive ? (
            <Text style={[styles.recordButtonText, recording && styles.recordButtonTextActive]}>
              {recordWillCancel ? "松开取消" : "松开发送"}
            </Text>
          ) : null}
        </Pressable>
        <TouchableOpacity
          style={[styles.mediaButton, (sending || uploadingMedia) && styles.disabledButton]}
          onPress={sendImage}
          disabled={sending || uploadingMedia}
        >
          {uploadingMedia ? (
            <ActivityIndicator size="small" color={colors.accent} />
          ) : (
            <Ionicons name="image-outline" size={20} color={colors.accent} />
          )}
        </TouchableOpacity>
        <TextInput
          style={styles.input}
          value={input}
          onChangeText={setInput}
          placeholder="问点什么..."
          placeholderTextColor={colors.muted}
          multiline
        />
        <TouchableOpacity style={[styles.sendButton, sending && styles.disabledButton]} onPress={sendMessage}>
          <Ionicons name="send" size={18} color={colors.surface} />
        </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
      <Modal
        visible={!!previewImageUrl}
        transparent
        animationType="fade"
        onRequestClose={() => setPreviewImageUrl(null)}
      >
        <Pressable style={styles.previewOverlay} onPress={() => setPreviewImageUrl(null)}>
          <View style={styles.previewHeader}>
            <TouchableOpacity style={styles.previewCloseButton} onPress={() => setPreviewImageUrl(null)}>
              <Ionicons name="close" size={24} color={colors.surface} />
            </TouchableOpacity>
          </View>
          {previewImageUrl ? (
            <Pressable style={styles.previewContent} onPress={(event) => event.stopPropagation()}>
              <ScrollView
                contentContainerStyle={styles.previewScrollContent}
                maximumZoomScale={4}
                minimumZoomScale={1}
                centerContent
              >
                <Image source={{ uri: previewImageUrl }} style={styles.previewImage} resizeMode="contain" />
              </ScrollView>
            </Pressable>
          ) : null}
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  audioInfo: {
    flex: 1,
    gap: 6,
    minWidth: 0
  },
  audioBubble: {
    alignItems: "center",
    backgroundColor: "#EEF2FF",
    borderRadius: 12,
    flexDirection: "row",
    gap: 8,
    marginBottom: 8,
    maxWidth: "100%",
    minWidth: 0,
    overflow: "hidden",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  audioBubbleMine: {
    backgroundColor: "#0F766E"
  },
  audioText: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "600",
    flexShrink: 1
  },
  audioTextMine: {
    color: colors.surface,
    fontSize: 13,
    fontWeight: "600",
    flexShrink: 1
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
    lineHeight: 22
  },
  chatBox: {
    flex: 1,
    gap: 10,
    marginTop: 12,
    minHeight: 0
  },
  composer: {
    alignItems: "flex-end",
    backgroundColor: colors.background,
    borderTopColor: colors.border,
    borderTopWidth: 1,
    flexDirection: "row",
    gap: 10,
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
    minWidth: 0,
    paddingHorizontal: 12,
    paddingVertical: 12
  },
  mediaButton: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    height: 48,
    justifyContent: "center",
    width: 48
  },
  recordButton: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: "row",
    gap: 0,
    height: 48,
    justifyContent: "center",
    width: 48
  },
  recordButtonActive: {
    backgroundColor: colors.error,
    borderColor: colors.error
  },
  recordButtonExpanded: {
    gap: 8,
    paddingHorizontal: 18,
    transform: [{ scale: 1.06 }],
    width: 126
  },
  recordButtonPressed: {
    backgroundColor: "#F8FAFC"
  },
  recordButtonText: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "700"
  },
  recordButtonTextActive: {
    color: colors.surface
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
  messageImage: {
    borderRadius: 12,
    height: 200,
    marginBottom: 8,
    width: 200
  },
  messageList: {
    flexGrow: 1,
    gap: 10,
    paddingBottom: 18
  },
  bottomAnchor: {
    height: 1
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
    alignItems: "flex-start",
    minWidth: 0
  },
  previewCloseButton: {
    alignItems: "center",
    height: 40,
    justifyContent: "center",
    width: 40
  },
  previewContent: {
    flex: 1,
    width: "100%"
  },
  previewHeader: {
    alignItems: "flex-end",
    paddingHorizontal: 16,
    paddingTop: 48
  },
  previewImage: {
    height: "100%",
    width: "100%"
  },
  previewOverlay: {
    backgroundColor: "rgba(15, 23, 42, 0.96)",
    flex: 1
  },
  recordHintOverlay: {
    alignItems: "center",
    paddingHorizontal: 18,
    paddingTop: 10
  },
  recordHintCard: {
    alignItems: "center",
    backgroundColor: "#D1FAE5",
    borderRadius: 16,
    gap: 6,
    paddingHorizontal: 18,
    paddingVertical: 12,
    width: "100%"
  },
  recordHintCardCancel: {
    backgroundColor: "rgba(220, 38, 38, 0.92)"
  },
  recordHintTitle: {
    color: "#065F46",
    fontSize: 14,
    fontWeight: "800"
  },
  recordHintSubtitle: {
    color: "#047857",
    fontSize: 12,
    textAlign: "center"
  },
  previewScrollContent: {
    alignItems: "center",
    flexGrow: 1,
    justifyContent: "center"
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
    overflow: "hidden",
    padding: 12
  },
  toolText: {
    color: colors.muted,
    fontSize: 12,
    flexShrink: 1,
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
  },
  waveBar: {
    backgroundColor: "#C7D2FE",
    borderRadius: 999,
    height: 6,
    width: 6
  },
  waveBarActive: {
    backgroundColor: colors.accent
  },
  waveBarActiveMine: {
    backgroundColor: colors.surface
  },
  waveBarMine: {
    backgroundColor: "rgba(255,255,255,0.35)"
  },
  waveTrack: {
    flexDirection: "row",
    flexWrap: "nowrap",
    gap: 4,
    maxWidth: "100%",
    minWidth: 0
  },
  waveTrackMine: {
    opacity: 0.95
  }
});
