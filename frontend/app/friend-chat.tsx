import { Ionicons } from "@expo/vector-icons";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";
import { Stack, useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import { useHeaderHeight } from "@react-navigation/elements";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Audio } from "expo-av";

import { Screen } from "@/components/Screen";
import {
  FRIEND_MESSAGE_TYPE_LABELS,
  FriendMessage,
  FriendMessageType,
  friendService
} from "@/services/friendService";
import {
  pickChatImage,
  requestAudioPermission,
  startAudioRecording,
  stopAudioRecording,
  toAttachmentPayload,
  uploadChatAudio,
  uploadChatImage
} from "@/services/chatMediaService";
import { useAuthStore } from "@/store/authStore";
import { colors } from "@/theme/colors";
import { formatDateTimeCn } from "@/utils/time";

const QUICK_INTERACTIONS: Array<{
  type: Exclude<FriendMessageType, "text">;
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
  content: string;
  backgroundColor: string;
  iconColor: string;
}> = [
  {
    type: "cheer",
    label: "加油",
    icon: "sparkles-outline",
    content: "今天也继续加油！",
    backgroundColor: "#CCFBF1",
    iconColor: colors.accent
  },
  {
    type: "celebrate",
    label: "庆祝",
    icon: "trophy-outline",
    content: "太棒了，必须给你庆祝一下！🎉",
    backgroundColor: "#FEF3C7",
    iconColor: "#D97706"
  },
  {
    type: "hug",
    label: "抱抱",
    icon: "heart-outline",
    content: "先给你一个抱抱，我们慢慢来 🤗",
    backgroundColor: "#FCE7F3",
    iconColor: "#DB2777"
  },
  {
    type: "coffee",
    label: "咖啡",
    icon: "cafe-outline",
    content: "奖励你一杯咖啡，辛苦啦 ☕",
    backgroundColor: "#EDE9FE",
    iconColor: "#7C3AED"
  },
  {
    type: "poke",
    label: "提醒",
    icon: "notifications-outline",
    content: "戳戳你，别忘了今天的小目标哦 👀",
    backgroundColor: "#DBEAFE",
    iconColor: "#2563EB"
  }
];

export default function FriendChatScreen() {
  const POLL_INTERVAL_MS = 1000;
  const router = useRouter();
  const { friendUserId, friendName } = useLocalSearchParams<{ friendUserId: string; friendName?: string }>();
  const userId = useAuthStore((state) => state.user?.id ?? "");
  const [messages, setMessages] = useState<FriendMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [sending, setSending] = useState(false);
  const [uploadingMedia, setUploadingMedia] = useState(false);
  const [recording, setRecording] = useState<Audio.Recording | null>(null);
  const [playingAudioId, setPlayingAudioId] = useState<string | null>(null);
  const [audioProgress, setAudioProgress] = useState<Record<string, number>>({});
  const [previewImageUrl, setPreviewImageUrl] = useState<string | null>(null);
  const [composerFocused, setComposerFocused] = useState(false);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const scrollViewRef = useRef<ScrollView | null>(null);
  const soundRef = useRef<Audio.Sound | null>(null);
  const hasInitialScrolledRef = useRef(false);
  const headerHeight = useHeaderHeight();
  const insets = useSafeAreaInsets();

  const title = useMemo(() => friendName || "聊天", [friendName]);

  const scrollToBottom = useCallback((animated = true) => {
    requestAnimationFrame(() => {
      scrollViewRef.current?.scrollToEnd({ animated });
    });
  }, []);

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
        if (!hasInitialScrolledRef.current) {
          hasInitialScrolledRef.current = true;
          setTimeout(() => scrollToBottom(false), 0);
        }
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

  const stopPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  }, []);

  const startPolling = useCallback(() => {
    if (pollingRef.current || composerFocused || sending) {
      return;
    }
    pollingRef.current = setInterval(() => {
      void loadMessages(true);
    }, POLL_INTERVAL_MS);
  }, [composerFocused, loadMessages, sending]);

  useEffect(() => {
    void loadMessages();
  }, [loadMessages]);

  useFocusEffect(
    useCallback(() => {
      void loadMessages(true);
      startPolling();

      return () => {
        stopPolling();
      };
    }, [loadMessages, startPolling, stopPolling])
  );

  useEffect(() => {
    if (composerFocused || sending) {
      stopPolling();
      return;
    }
    startPolling();
    return () => {
      stopPolling();
    };
  }, [composerFocused, sending, startPolling, stopPolling]);

  async function onRefresh() {
    setRefreshing(true);
    await loadMessages(true);
  }

  async function sendMessage(type: FriendMessageType = "text") {
    if (!friendUserId || sending) return;
    const quickInteraction = QUICK_INTERACTIONS.find((item) => item.type === type);
    const content = quickInteraction ? quickInteraction.content : draft.trim();
    if (!content) return;

    setSending(true);
    try {
      const response = await friendService.sendMessage(friendUserId, content, type);
      if (!response.success) {
        Alert.alert("发送失败", response.error?.message ?? "请稍后重试");
        return;
      }
      setDraft("");
      scrollToBottom(true);
      await loadMessages(true);
      scrollToBottom(true);
    } finally {
      setSending(false);
    }
  }

  async function sendImage() {
    if (!friendUserId || sending || uploadingMedia) {
      return;
    }
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
      setSending(true);
      const response = await friendService.sendMessage(
        friendUserId,
        "",
        "image",
        toAttachmentPayload(uploaded)
      );
      if (!response.success) {
        Alert.alert("发送失败", response.error?.message ?? "请稍后重试");
        return;
      }
      await loadMessages(true);
      scrollToBottom(true);
    } catch (error) {
      Alert.alert("发送失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setUploadingMedia(false);
      setSending(false);
    }
  }

  async function toggleRecordAudio() {
    if (!friendUserId || sending || uploadingMedia) {
      return;
    }
    if (recording) {
      try {
        setUploadingMedia(true);
        const result = await stopAudioRecording(recording);
        setRecording(null);
        const uploaded = await uploadChatAudio({
          uri: result.uri,
          fileSize: result.fileSize,
          durationSeconds: result.durationSeconds,
          mimeType: Platform.OS === "ios" ? "audio/m4a" : "audio/mp4"
        });
        setSending(true);
        const response = await friendService.sendMessage(
          friendUserId,
          "",
          "audio" as any,
          toAttachmentPayload(uploaded)
        );
        if (!response.success) {
          Alert.alert("发送失败", response.error?.message ?? "请稍后重试");
          return;
        }
        await loadMessages(true);
        scrollToBottom(true);
      } catch (error) {
        Alert.alert("语音发送失败", error instanceof Error ? error.message : "请稍后重试");
      } finally {
        setUploadingMedia(false);
        setSending(false);
      }
      return;
    }

    try {
      const granted = await requestAudioPermission();
      if (!granted) {
        Alert.alert("需要权限", "请先允许麦克风权限");
        return;
      }
      const nextRecording = await startAudioRecording();
      setRecording(nextRecording);
    } catch (error) {
      Alert.alert("录音失败", error instanceof Error ? error.message : "请稍后重试");
    }
  }

  async function playAudio(message: FriendMessage) {
    if (!message.attachment?.url) {
      return;
    }
    try {
      if (soundRef.current) {
        await soundRef.current.unloadAsync();
        soundRef.current = null;
      }
      setPlayingAudioId(message.id);
      const { sound } = await Audio.Sound.createAsync(
        { uri: message.attachment.url },
        { shouldPlay: true },
        (status) => {
          if (status.isLoaded) {
            const duration = status.durationMillis ?? 1;
            const position = status.positionMillis ?? 0;
            setAudioProgress((current) => ({ ...current, [message.id]: Math.max(0, Math.min(1, position / duration)) }));
          }
          if (!status.isLoaded || !status.didJustFinish) {
            return;
          }
          setPlayingAudioId(null);
          setAudioProgress((current) => ({ ...current, [message.id]: 0 }));
        }
      );
      soundRef.current = sound;
    } catch (error) {
      setPlayingAudioId(null);
      Alert.alert("播放失败", error instanceof Error ? error.message : "请稍后重试");
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
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        keyboardVerticalOffset={headerHeight}
      >
        <Screen scrollable={false} style={styles.screen} contentContainerStyle={styles.screenContent}>
          {loading ? (
            <View style={styles.centerBlock}>
              <ActivityIndicator color={colors.accent} />
            </View>
          ) : (
            <ScrollView
              ref={scrollViewRef}
              contentContainerStyle={styles.messageList}
              keyboardShouldPersistTaps="handled"
              refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.accent} />}
            >
              {messages.length === 0 ? (
                <Text style={styles.emptyText}>还没有消息，发一句鼓励开始吧。</Text>
              ) : (
                messages.map((item) => {
                  const mine = item.fromUserId === userId;
                  return (
                    <View key={item.id} style={[styles.messageBubble, mine ? styles.messageBubbleMine : styles.messageBubbleOther]}>
                      {item.attachment?.kind === "image" && item.attachment.url ? (
                        <TouchableOpacity activeOpacity={0.9} onPress={() => setPreviewImageUrl(item.attachment?.url ?? null)}>
                          <Image
                            source={{ uri: item.attachment.url }}
                            style={styles.messageImage}
                            resizeMode="cover"
                          />
                        </TouchableOpacity>
                      ) : null}
                      {item.attachment?.kind === "audio" ? (
                        <TouchableOpacity
                          style={[styles.audioBubble, mine && styles.audioBubbleMine]}
                          onPress={() => playAudio(item)}
                        >
                          <Ionicons
                            name={playingAudioId === item.id ? "pause-circle-outline" : "play-circle-outline"}
                            size={20}
                            color={mine ? colors.surface : colors.accent}
                          />
                          <View style={styles.audioInfo}>
                            <Text style={[styles.audioText, mine && styles.audioTextMine]}>
                              {item.attachment.durationSeconds ? `${item.attachment.durationSeconds}s 语音` : "语音消息"}
                            </Text>
                            <View style={[styles.waveTrack, mine && styles.waveTrackMine]}>
                              {Array.from({ length: 16 }).map((_, index) => {
                                const activeCount = Math.round((audioProgress[item.id] ?? 0) * 16);
                                return (
                                  <View
                                    key={`${item.id}-wave-${index}`}
                                    style={[
                                      styles.waveBar,
                                      mine && styles.waveBarMine,
                                      index < activeCount && (mine ? styles.waveBarActiveMine : styles.waveBarActive)
                                    ]}
                                  />
                                );
                              })}
                            </View>
                          </View>
                        </TouchableOpacity>
                      ) : null}
                      <Text style={[styles.messageText, mine && styles.messageTextMine]}>{item.content}</Text>
                      <Text style={[styles.messageMeta, mine && styles.messageMetaMine]}>
                        {FRIEND_MESSAGE_TYPE_LABELS[item.type]} · {formatDateTimeCn(item.createdAt)}
                      </Text>
                    </View>
                  );
                })
              )}
              <View style={styles.bottomAnchor} />
            </ScrollView>
          )}
        </Screen>

        <View style={styles.quickActionWrap}>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.quickActionList}
            keyboardShouldPersistTaps="handled"
          >
            {QUICK_INTERACTIONS.map((item) => (
              <TouchableOpacity
                key={item.type}
                style={[styles.quickActionButton, { backgroundColor: item.backgroundColor }, sending && styles.disabledButton]}
                onPress={() => sendMessage(item.type)}
                disabled={sending}
              >
                <Ionicons name={item.icon} size={16} color={item.iconColor} />
                <Text style={styles.quickActionText}>{item.label}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>

        <View style={[styles.composer, { paddingBottom: Math.max(insets.bottom, 12) }]}>
          <TouchableOpacity
            style={[styles.mediaButton, (sending || uploadingMedia) && styles.disabledButton]}
            onPress={toggleRecordAudio}
            disabled={sending || uploadingMedia}
          >
            <Ionicons
              name={recording ? "stop-circle-outline" : "mic-outline"}
              size={20}
              color={recording ? colors.error : colors.accent}
            />
          </TouchableOpacity>
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
            value={draft}
            onChangeText={setDraft}
            onFocus={() => setComposerFocused(true)}
            onBlur={() => setComposerFocused(false)}
            placeholder="发条消息，鼓励一下好友"
            placeholderTextColor={colors.muted}
          />
          <TouchableOpacity
            style={[styles.sendButton, sending && styles.disabledButton]}
            onPress={() => sendMessage("text")}
            disabled={sending}
          >
            <Ionicons name="send-outline" size={18} color={colors.surface} />
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
    gap: 6
  },
  audioBubble: {
    alignItems: "center",
    backgroundColor: "#EEF2FF",
    borderRadius: 12,
    flexDirection: "row",
    gap: 8,
    marginBottom: 8,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  audioBubbleMine: {
    backgroundColor: "#0F766E"
  },
  audioText: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "600"
  },
  audioTextMine: {
    color: colors.surface
  },
  centerBlock: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center"
  },
  composer: {
    alignItems: "center",
    backgroundColor: colors.background,
    borderTopColor: colors.border,
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
  mediaButton: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 14,
    borderWidth: 1,
    height: 46,
    justifyContent: "center",
    width: 46
  },
  messageImage: {
    borderRadius: 12,
    height: 180,
    marginBottom: 8,
    width: 180
  },
  quickActionButton: {
    alignItems: "center",
    borderRadius: 999,
    flexDirection: "row",
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  quickActionList: {
    gap: 8,
    paddingHorizontal: 18,
    paddingVertical: 10
  },
  quickActionText: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "600"
  },
  quickActionWrap: {
    backgroundColor: colors.background,
    borderTopColor: colors.border,
    borderTopWidth: 1
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
    paddingBottom: 18,
    paddingTop: 4
  },
  bottomAnchor: {
    height: 1
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
  previewScrollContent: {
    alignItems: "center",
    flexGrow: 1,
    justifyContent: "center"
  },
  screen: {
    flex: 1
  },
  screenContent: {
    flex: 1,
    paddingBottom: 0,
    paddingTop: 0
  },
  sendButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 14,
    height: 46,
    justifyContent: "center",
    width: 46
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
    gap: 4
  },
  waveTrackMine: {
    opacity: 0.95
  }
});
