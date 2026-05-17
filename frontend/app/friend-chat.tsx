import { Ionicons } from "@expo/vector-icons";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  PanResponder,
  ActivityIndicator,
  Alert,
  Image,
  Keyboard,
  Modal,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  Vibration,
  View
} from "react-native";
import { Stack, useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Audio } from "expo-av";

import { Screen } from "@/components/Screen";
import {
  FriendMessage,
  FriendMessageType,
  friendService
} from "@/services/friendService";
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
import { useAuthStore } from "@/store/authStore";
import { useFriendBadgeStore } from "@/store/friendBadgeStore";
import { colors } from "@/theme/colors";
import { formatDateCn, formatDateTimeCn } from "@/utils/time";
import { friendRealtimeService } from "@/services/friendRealtimeService";

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
    content: "加油！",
    backgroundColor: "#CCFBF1",
    iconColor: colors.accent
  },
  {
    type: "celebrate",
    label: "庆祝",
    icon: "trophy-outline",
    content: "太棒了！🎉",
    backgroundColor: "#FEF3C7",
    iconColor: "#D97706"
  },
  {
    type: "hug",
    label: "抱抱",
    icon: "heart-outline",
    content: "抱抱你 🤗",
    backgroundColor: "#FCE7F3",
    iconColor: "#DB2777"
  },
  {
    type: "coffee",
    label: "咖啡",
    icon: "cafe-outline",
    content: "请你喝咖啡 ☕",
    backgroundColor: "#EDE9FE",
    iconColor: "#7C3AED"
  },
  {
    type: "poke",
    label: "提醒",
    icon: "notifications-outline",
    content: "别忘了今天目标 👀",
    backgroundColor: "#DBEAFE",
    iconColor: "#2563EB"
  }
];

const ATTACHMENT_PLACEHOLDER_PATTERN = /^\[(语音|图片)(消息)?\]$/;
const HISTORY_DIVIDER_GAP_MS = 60 * 60 * 1000;
const MESSAGE_PAGE_SIZE = 50;
const LOAD_MORE_TRIGGER_OFFSET = 80;

function mergeMessagesPreservingMediaUrl(previous: FriendMessage[], incoming: FriendMessage[]) {
  const previousById = new Map(previous.map((message) => [message.id, message]));
  const merged = new Map<string, FriendMessage>();
  [...previous, ...incoming].forEach((message) => {
    const previousMessage = previousById.get(message.id);
    if (
      previousMessage?.attachment?.url &&
      message.attachment &&
      previousMessage.attachment.assetId === message.attachment.assetId &&
      previousMessage.attachment.kind === message.attachment.kind &&
      !message.attachment.url
    ) {
      merged.set(message.id, {
        ...message,
        attachment: {
          ...message.attachment,
          url: previousMessage.attachment.url
        }
      });
      return;
    }
    merged.set(message.id, message);
  });
  return Array.from(merged.values()).sort((left, right) => {
    const timeDiff = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
    return timeDiff !== 0 ? timeDiff : left.id.localeCompare(right.id);
  });
}

function shouldRenderMessageText(content: string | null | undefined) {
  const normalized = content?.trim();
  return !!normalized && !ATTACHMENT_PLACEHOLDER_PATTERN.test(normalized);
}

function shouldShowHistoryDivider(currentCreatedAt: string, previousCreatedAt?: string) {
  if (!previousCreatedAt) {
    return true;
  }

  const currentDate = new Date(currentCreatedAt);
  const previousDate = new Date(previousCreatedAt);
  if (Number.isNaN(currentDate.getTime()) || Number.isNaN(previousDate.getTime())) {
    return false;
  }

  if (formatDateCn(currentDate) !== formatDateCn(previousDate)) {
    return true;
  }

  return currentDate.getTime() - previousDate.getTime() >= HISTORY_DIVIDER_GAP_MS;
}

function getDisplayInitial(value: string | null | undefined) {
  const normalized = value?.trim();
  if (!normalized) {
    return "我";
  }
  return normalized.slice(0, 1).toUpperCase();
}

export default function FriendChatScreen() {
  const router = useRouter();
  const { friendUserId, friendName, friendAvatarUrl: routeFriendAvatarUrl } = useLocalSearchParams<{
    friendUserId: string;
    friendName?: string;
    friendAvatarUrl?: string;
  }>();
  const user = useAuthStore((state) => state.user);
  const userId = useAuthStore((state) => state.user?.id ?? "");
  const clearConversationUnread = useFriendBadgeStore((state) => state.clearConversationUnread);
  const sharedConversations = useFriendBadgeStore((state) => state.conversations);
  const replaceFriendBadgeConversations = useFriendBadgeStore((state) => state.replaceConversations);
  const upsertConversation = useFriendBadgeStore((state) => state.upsertConversation);
  const [messages, setMessages] = useState<FriendMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [sending, setSending] = useState(false);
  const [uploadingMedia, setUploadingMedia] = useState(false);
  const [hasMoreHistory, setHasMoreHistory] = useState(true);
  const [recording, setRecording] = useState<Audio.Recording | null>(null);
  const [playingAudioId, setPlayingAudioId] = useState<string | null>(null);
  const [audioProgress, setAudioProgress] = useState<Record<string, number>>({});
  const [previewImageUrl, setPreviewImageUrl] = useState<string | null>(null);
  const [composerFocused, setComposerFocused] = useState(false);
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  const [recordTouchActive, setRecordTouchActive] = useState(false);
  const [recordGestureActive, setRecordGestureActive] = useState(false);
  const [recordWillCancel, setRecordWillCancel] = useState(false);
  const recordFinalizingRef = useRef(false);
  const recordStartingRef = useRef(false);
  const recordHoldTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cancelZoneViewRef = useRef<View | null>(null);
  const cancelZoneRef = useRef<{ x: number; y: number; width: number; height: number } | null>(null);
  const hasEnteredCancelZoneRef = useRef(false);
  const recordWillCancelRef = useRef(false);
  const scrollViewRef = useRef<ScrollView | null>(null);
  const scrollOffsetRef = useRef(0);
  const contentHeightRef = useRef(0);
  const pendingScrollRestoreRef = useRef<{ previousOffset: number; previousHeight: number } | null>(null);
  const soundRef = useRef<Audio.Sound | null>(null);
  const hasInitialScrolledRef = useRef(false);
  const messagesRef = useRef<FriendMessage[]>([]);
  const loadingOlderRef = useRef(false);
  const avatarRefreshInFlightRef = useRef(false);
  const insets = useSafeAreaInsets();

  const title = useMemo(() => friendName || "聊天", [friendName]);
  const selfAvatarLabel = useMemo(
    () => getDisplayInitial(user?.displayName || user?.email),
    [user?.displayName, user?.email]
  );
  const friendAvatarLabel = useMemo(() => getDisplayInitial(title), [title]);
  const friendConversation = useMemo(
    () => sharedConversations.find((item) => item.friendUserId === friendUserId),
    [friendUserId, sharedConversations]
  );
  const friendAvatarUrl = useMemo(
    () => friendConversation?.friendAvatarUrl ?? routeFriendAvatarUrl ?? null,
    [friendConversation?.friendAvatarUrl, routeFriendAvatarUrl]
  );

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  const scrollToBottom = useCallback((animated = true) => {
    requestAnimationFrame(() => {
      scrollViewRef.current?.scrollToEnd({ animated });
    });
  }, []);

  const updateConversationSummary = useCallback(
    (lastMessage: string, lastMessageType: FriendMessage["type"], lastMessageAt: string) => {
      if (!friendUserId) {
        return;
      }

      upsertConversation({
        friendUserId,
        friendDisplayName: friendConversation?.friendDisplayName ?? title,
        friendEmail: friendConversation?.friendEmail ?? "",
        friendAvatarAssetId: friendConversation?.friendAvatarAssetId ?? null,
        friendAvatarUrl: friendConversation?.friendAvatarUrl ?? routeFriendAvatarUrl ?? null,
        lastMessage,
        lastMessageType,
        lastMessageAt,
        unreadCount: 0
      });
    },
    [
      friendConversation?.friendAvatarAssetId,
      friendConversation?.friendAvatarUrl,
      friendConversation?.friendDisplayName,
      friendConversation?.friendEmail,
      friendUserId,
      routeFriendAvatarUrl,
      title,
      upsertConversation
    ]
  );

  const refreshConversationContext = useCallback(async () => {
    if (avatarRefreshInFlightRef.current) {
      return;
    }
    avatarRefreshInFlightRef.current = true;
    try {
      const response = await friendService.listConversations();
      if (response.success && response.data) {
        replaceFriendBadgeConversations(response.data);
      }
    } finally {
      avatarRefreshInFlightRef.current = false;
    }
  }, [replaceFriendBadgeConversations]);

  const loadLatestMessages = useCallback(async ({ silent = false, mergeIntoCurrent = false }: { silent?: boolean; mergeIntoCurrent?: boolean } = {}) => {
    if (!friendUserId) {
      return;
    }
    if (!silent) {
      setLoading(true);
    }
    try {
      const response = await friendService.listMessages(friendUserId, { limit: MESSAGE_PAGE_SIZE });
      if (response.success && response.data) {
        setMessages((current) => {
          if (mergeIntoCurrent) {
            return mergeMessagesPreservingMediaUrl(current, response.data?.messages ?? []);
          }
          return mergeMessagesPreservingMediaUrl([], response.data?.messages ?? []);
        });
        if (!mergeIntoCurrent || messagesRef.current.length === 0) {
          setHasMoreHistory(response.data.hasMore);
        }
        await friendService.markConversationRead(friendUserId);
        clearConversationUnread(friendUserId);
        if (!hasInitialScrolledRef.current) {
          hasInitialScrolledRef.current = true;
          setTimeout(() => scrollToBottom(false), 0);
        }
      } else {
        setMessages([]);
        setHasMoreHistory(false);
      }
    } catch (error) {
      Alert.alert("加载失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [clearConversationUnread, friendUserId, scrollToBottom]);

  const loadOlderMessages = useCallback(async () => {
    if (!friendUserId || loading || loadingOlderRef.current || !hasMoreHistory || messagesRef.current.length === 0) {
      return;
    }
    const oldestMessage = messagesRef.current[0];
    if (!oldestMessage) {
      return;
    }
    loadingOlderRef.current = true;
    setLoadingOlder(true);
    try {
      const response = await friendService.listMessages(friendUserId, {
        limit: MESSAGE_PAGE_SIZE,
        beforeCreatedAt: oldestMessage.createdAt,
        beforeId: oldestMessage.id
      });
      if (response.success && response.data) {
        pendingScrollRestoreRef.current = {
          previousOffset: scrollOffsetRef.current,
          previousHeight: contentHeightRef.current
        };
        setMessages((current) => mergeMessagesPreservingMediaUrl(current, [...(response.data?.messages ?? []), ...current]));
        setHasMoreHistory(response.data.hasMore);
      }
    } catch (error) {
      Alert.alert("加载历史失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      loadingOlderRef.current = false;
      setLoadingOlder(false);
    }
  }, [friendUserId, hasMoreHistory, loading]);

  useEffect(() => {
    hasInitialScrolledRef.current = false;
    setHasMoreHistory(true);
    setMessages([]);
    void loadLatestMessages();
  }, [loadLatestMessages]);

  useEffect(() => {
    const showEvent = Platform.OS === "ios" ? "keyboardWillShow" : "keyboardDidShow";
    const hideEvent = Platform.OS === "ios" ? "keyboardWillHide" : "keyboardDidHide";
    const showSub = Keyboard.addListener(showEvent, (event) => {
      const nextHeight = Math.max(0, (event.endCoordinates?.height ?? 0) - insets.bottom);
      setKeyboardHeight(nextHeight);
      setTimeout(() => scrollToBottom(false), 80);
    });
    const hideSub = Keyboard.addListener(hideEvent, () => {
      setKeyboardHeight(0);
    });
    return () => {
      showSub.remove();
      hideSub.remove();
    };
  }, [insets.bottom, scrollToBottom]);

  useFocusEffect(
    useCallback(() => {
      if (messagesRef.current.length === 0) {
        void loadLatestMessages({ silent: true });
      } else {
        void loadLatestMessages({ silent: true, mergeIntoCurrent: true });
      }
      const unsubscribe = friendRealtimeService.subscribe({
        onMessage: (message) => {
          const related =
            (message.fromUserId === friendUserId && message.toUserId === userId) ||
            (message.fromUserId === userId && message.toUserId === friendUserId);
          if (!related) {
            return;
          }
          setMessages((current) => {
            if (current.some((item) => item.id === message.id)) {
              return current;
            }
            return [...current, message];
          });
          if (message.fromUserId === friendUserId && message.toUserId === userId) {
            void friendService.markConversationRead(friendUserId);
            clearConversationUnread(friendUserId);
          }
        },
        onConversationRead: (payload) => {
          if (payload.friendUserId !== friendUserId) {
            return;
          }
          if (payload.conversation) {
            upsertConversation(payload.conversation);
          }
        }
      });

      return () => {
        unsubscribe();
      };
    }, [friendUserId, loadLatestMessages, upsertConversation, userId])
  );

  async function onRefresh() {
    setRefreshing(true);
    await loadLatestMessages({ silent: true, mergeIntoCurrent: true });
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
      updateConversationSummary(content, type, response.data?.createdAt ?? new Date().toISOString());
      if (response.data) {
        setMessages((current) => (current.some((item) => item.id === response.data!.id) ? current : [...current, response.data!]));
      }
      setDraft("");
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
      updateConversationSummary("[图片消息]", "image", response.data?.createdAt ?? new Date().toISOString());
      if (response.data) {
        setMessages((current) => (current.some((item) => item.id === response.data!.id) ? current : [...current, response.data!]));
      }
      scrollToBottom(true);
    } catch (error) {
      Alert.alert("发送失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setUploadingMedia(false);
      setSending(false);
    }
  }

  async function toggleRecordAudio() {
    if (!friendUserId || sending || uploadingMedia || recording || recordStartingRef.current) {
      return;
    }
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
  }

  async function finishRecordAudio(currentRecording: Audio.Recording) {
    if (!friendUserId) {
      return;
    }
    try {
      setUploadingMedia(true);
      const result = await stopAudioRecording(currentRecording);
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
      updateConversationSummary("[语音消息]", "audio", response.data?.createdAt ?? new Date().toISOString());
      if (response.data) {
        setMessages((current) => (current.some((item) => item.id === response.data!.id) ? current : [...current, response.data!]));
      }
      scrollToBottom(true);
    } catch (error) {
      Alert.alert("语音发送失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setUploadingMedia(false);
      setSending(false);
    }
  }

  async function finalizeRecord(send: boolean) {
    if (recordStartingRef.current) {
      setTimeout(() => {
        void finalizeRecord(send);
      }, 80);
      return;
    }
    if (!recording || recordFinalizingRef.current) {
      setRecordTouchActive(false);
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
      hasEnteredCancelZoneRef.current = false;
      setRecordTouchActive(false);
      setRecordGestureActive(false);
      setRecordWillCancel(false);
      recordFinalizingRef.current = false;
    }
  }

  const clearRecordHoldTimer = useCallback(() => {
    if (recordHoldTimerRef.current) {
      clearTimeout(recordHoldTimerRef.current);
      recordHoldTimerRef.current = null;
    }
  }, []);

  const updateCancelZoneState = useCallback((pageX: number, pageY: number) => {
    const zone = cancelZoneRef.current;
    if (!zone) {
      recordWillCancelRef.current = false;
      setRecordWillCancel(false);
      return false;
    }
    const inside =
      pageX >= zone.x &&
      pageX <= zone.x + zone.width &&
      pageY >= zone.y &&
      pageY <= zone.y + zone.height;

    recordWillCancelRef.current = inside;
    setRecordWillCancel(inside);
    if (inside && !hasEnteredCancelZoneRef.current) {
      hasEnteredCancelZoneRef.current = true;
      Vibration.vibrate(10);
    } else if (!inside) {
      hasEnteredCancelZoneRef.current = false;
    }
    return inside;
  }, []);

  const recordPanResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => !(sending || uploadingMedia),
        onStartShouldSetPanResponderCapture: () => recordTouchActive,
        onMoveShouldSetPanResponderCapture: () => recordTouchActive,
        onPanResponderTerminationRequest: () => false,
        onShouldBlockNativeResponder: () => true,
        onPanResponderGrant: () => {
          setRecordTouchActive(true);
          setRecordWillCancel(false);
          hasEnteredCancelZoneRef.current = false;
          clearRecordHoldTimer();
          recordHoldTimerRef.current = setTimeout(() => {
            setRecordGestureActive(true);
            void toggleRecordAudio();
          }, 180);
        },
        onPanResponderMove: (event) => {
          if (!recordGestureActive) {
            return;
          }
          updateCancelZoneState(event.nativeEvent.pageX, event.nativeEvent.pageY);
        },
        onPanResponderRelease: (event) => {
          clearRecordHoldTimer();
          if (!recordGestureActive && !recordStartingRef.current && !recording) {
            setRecordTouchActive(false);
            setRecordWillCancel(false);
            recordWillCancelRef.current = false;
            return;
          }
          const shouldCancel = recordGestureActive
            ? updateCancelZoneState(event.nativeEvent.pageX, event.nativeEvent.pageY)
            : recordWillCancelRef.current;
          void finalizeRecord(!shouldCancel);
        },
        onPanResponderTerminate: () => {
          clearRecordHoldTimer();
          if (!recordGestureActive && !recordStartingRef.current && !recording) {
            setRecordTouchActive(false);
            setRecordWillCancel(false);
            recordWillCancelRef.current = false;
            return;
          }
          void finalizeRecord(false);
        }
      }),
    [clearRecordHoldTimer, recordGestureActive, recording, sending, updateCancelZoneState, uploadingMedia]
  );

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
      <View style={[styles.container, { paddingBottom: keyboardHeight }]}>
        <Screen scrollable={false} style={styles.screen} contentContainerStyle={styles.screenContent} edges={["right", "bottom", "left"]}>
          {loading ? (
            <View style={styles.centerBlock}>
              <ActivityIndicator color={colors.accent} />
            </View>
          ) : (
            <ScrollView
              ref={scrollViewRef}
              contentContainerStyle={[
                styles.messageList,
                { paddingBottom: Math.max(insets.bottom + 24, 36) }
              ]}
              onContentSizeChange={(_, height) => {
                const pendingRestore = pendingScrollRestoreRef.current;
                if (pendingRestore) {
                  const delta = height - pendingRestore.previousHeight;
                  scrollViewRef.current?.scrollTo({
                    y: pendingRestore.previousOffset + Math.max(delta, 0),
                    animated: false
                  });
                  pendingScrollRestoreRef.current = null;
                }
                contentHeightRef.current = height;
              }}
              onScroll={(event) => {
                scrollOffsetRef.current = event.nativeEvent.contentOffset.y;
                if (scrollOffsetRef.current <= LOAD_MORE_TRIGGER_OFFSET) {
                  void loadOlderMessages();
                }
              }}
              scrollEventThrottle={16}
              keyboardShouldPersistTaps="handled"
              refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.accent} />}
            >
              {loadingOlder ? (
                <View style={styles.historyLoadingRow}>
                  <ActivityIndicator size="small" color={colors.accent} />
                  <Text style={styles.historyLoadingText}>正在加载更早消息...</Text>
                </View>
              ) : null}
              {messages.length === 0 ? (
                <Text style={styles.emptyText}>还没有消息，发一句鼓励开始吧。</Text>
              ) : (
                messages.map((item, index) => {
                  const mine = item.fromUserId === userId;
                  const previousMessage = index > 0 ? messages[index - 1] : undefined;
                  const showHistoryDivider = shouldShowHistoryDivider(item.createdAt, previousMessage?.createdAt);
                  return (
                    <View key={item.id} style={styles.messageGroup}>
                      {showHistoryDivider ? (
                        <View style={styles.historyDividerWrap}>
                          <Text style={styles.historyDividerText}>{formatDateTimeCn(item.createdAt)}</Text>
                        </View>
                      ) : null}
                      <View style={[styles.messageRow, mine ? styles.messageRowMine : styles.messageRowOther]}>
                        {!mine ? (
                          friendAvatarUrl ? (
                            <Image
                              source={{ uri: friendAvatarUrl }}
                              style={styles.avatarImage}
                              onError={() => {
                                void refreshConversationContext();
                              }}
                            />
                          ) : (
                            <View style={[styles.avatar, styles.avatarFriend]}>
                              <Text style={[styles.avatarText, styles.avatarTextDark]}>{friendAvatarLabel}</Text>
                            </View>
                          )
                        ) : null}
                        <View style={[styles.messageColumn, mine && styles.messageColumnMine]}>
                          <View style={[styles.messageBubble, mine ? styles.messageBubbleMine : styles.messageBubbleOther]}>
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
                                  {item.attachment.durationSeconds ? (
                                    <Text style={[styles.audioText, mine && styles.audioTextMine]}>
                                      {item.attachment.durationSeconds}s
                                    </Text>
                                  ) : null}
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
                            {shouldRenderMessageText(item.content) ? (
                              <Text style={[styles.messageText, mine && styles.messageTextMine]}>{item.content}</Text>
                            ) : null}
                            <Text style={[styles.messageMeta, mine && styles.messageMetaMine]}>
                              {formatDateTimeCn(item.createdAt)}
                            </Text>
                          </View>
                        </View>
                        {mine ? (
                          user?.avatarUrl ? (
                            <Image source={{ uri: user.avatarUrl }} style={styles.avatarImage} />
                          ) : (
                            <View style={[styles.avatar, styles.avatarMine]}>
                              <Text style={styles.avatarText}>{selfAvatarLabel}</Text>
                            </View>
                          )
                        ) : null}
                      </View>
                    </View>
                  );
                })
              )}
              <View style={styles.bottomAnchor} />
            </ScrollView>
          )}
        </Screen>

        <View style={[styles.quickActionWrap, { paddingBottom: 0 }]}>
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

        {recordGestureActive ? (
          <View style={styles.recordHintOverlay} pointerEvents="none">
            <View
              ref={cancelZoneViewRef}
              style={[styles.recordHintCard, recordWillCancel && styles.recordHintCardCancel]}
              onLayout={() => {
                requestAnimationFrame(() => {
                  cancelZoneViewRef.current?.measureInWindow((x, y, width, height) => {
                    cancelZoneRef.current = { x, y, width, height };
                  });
                });
              }}
            >
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
          <View
            style={[
              styles.recordButton,
              recordTouchActive && styles.recordButtonPressed,
              recordGestureActive && styles.recordButtonExpanded,
              recording && styles.recordButtonActive,
              (sending || uploadingMedia) && styles.disabledButton
            ]}
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
          </View>
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
            onFocus={() => {
              setComposerFocused(true);
              setTimeout(() => scrollToBottom(false), 80);
            }}
            onBlur={() => setComposerFocused(false)}
            placeholder="发消息..."
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
      </View>
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
  avatar: {
    alignItems: "center",
    borderRadius: 999,
    height: 34,
    justifyContent: "center",
    width: 34
  },
  avatarFriend: {
    backgroundColor: "#E0E7FF"
  },
  avatarImage: {
    borderRadius: 999,
    height: 34,
    width: 34
  },
  avatarMine: {
    backgroundColor: colors.accent
  },
  avatarText: {
    color: colors.surface,
    fontSize: 13,
    fontWeight: "800"
  },
  avatarTextDark: {
    color: "#3730A3"
  },
  audioInfo: {
    flex: 1,
    gap: 6,
    minWidth: 0
  },
  audioBubble: {
    alignItems: "center",
    backgroundColor: "#ECFDF5",
    borderColor: "#A7F3D0",
    borderRadius: 12,
    borderWidth: 1,
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
    backgroundColor: "#115E59",
    borderColor: "rgba(255,255,255,0.22)"
  },
  audioText: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "600",
    flexShrink: 1
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
    lineHeight: 21,
    paddingVertical: 20,
    textAlign: "center"
  },
  historyDividerText: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: "600"
  },
  historyDividerWrap: {
    alignItems: "center",
    alignSelf: "center",
    backgroundColor: "#E2E8F0",
    borderRadius: 999,
    marginBottom: 2,
    paddingHorizontal: 12,
    paddingVertical: 6
  },
  historyLoadingRow: {
    alignItems: "center",
    flexDirection: "row",
    gap: 8,
    justifyContent: "center",
    paddingBottom: 4
  },
  historyLoadingText: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: "600"
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
    minWidth: 0,
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
  recordButton: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 14,
    borderWidth: 1,
    flexDirection: "row",
    gap: 0,
    height: 46,
    justifyContent: "center",
    width: 46
  },
  recordButtonActive: {
    backgroundColor: colors.error,
    borderColor: colors.error
  },
  recordButtonExpanded: {
    gap: 8,
    paddingHorizontal: 18,
    transform: [{ scale: 1.06 }],
    width: 124
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
    maxWidth: "100%",
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
  messageColumn: {
    maxWidth: "82%",
    minWidth: 0
  },
  messageColumnMine: {
    alignItems: "flex-end"
  },
  messageList: {
    flexGrow: 1,
    gap: 10,
    paddingBottom: 18,
    paddingTop: 4
  },
  messageGroup: {
    gap: 8
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
  messageRow: {
    alignItems: "flex-end",
    flexDirection: "row",
    gap: 10
  },
  messageRowMine: {
    justifyContent: "flex-end"
  },
  messageRowOther: {
    justifyContent: "flex-start"
  },
  messageText: {
    color: colors.text,
    fontSize: 14,
    flexShrink: 1,
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
    flexWrap: "nowrap",
    gap: 4,
    maxWidth: "100%",
    minWidth: 0
  },
  waveTrackMine: {
    opacity: 0.95
  }
});
