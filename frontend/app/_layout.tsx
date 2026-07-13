import { useEffect, useRef, useState } from "react";
import { AppState, Platform, Pressable, StyleSheet, Text, TextInput, Vibration, View } from "react-native";
import { Stack, useGlobalSearchParams, useRouter, useSegments } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { useAuthStore } from "@/store/authStore";
import { initDatabase } from "@/db/database";
import { authService } from "@/services/authService";
import { authStorage } from "@/services/authStorage";
import { deviceService } from "@/services/deviceService";
import { nativePushService } from "@/services/nativePushService";
import { friendRealtimeService } from "@/services/friendRealtimeService";
import { friendService } from "@/services/friendService";
import { appUpdateService } from "@/services/appUpdateService";
import { colors } from "@/theme/colors";
import { useFriendBadgeStore } from "@/store/friendBadgeStore";
import { useNewsBootstrapStore } from "@/store/newsBootstrapStore";
import { AppLaunchLoading } from "@/components/AppLaunchLoading";

const NEWS_LAUNCH_WAIT_MS = 3000;

export default function RootLayout() {
  const { isAuthenticated, isLoading, setLoading, restoreAuth, setAuth, clearAuth, token } = useAuthStore();
  const segments = useSegments();
  const globalParams = useGlobalSearchParams<{ friendUserId?: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const bannerTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const updateCheckStartedRef = useRef(false);
  const newsPrefetchStartedRef = useRef(false);
  const [showLaunch, setShowLaunch] = useState(true);
  const [launchReady, setLaunchReady] = useState(false);
  const [messageBanner, setMessageBanner] = useState<{
    friendUserId: string;
    friendName: string;
    content: string;
  } | null>(null);

  const TextComponent = Text as typeof Text & {
    defaultProps?: {
      allowFontScaling?: boolean;
      maxFontSizeMultiplier?: number;
    };
  };
  TextComponent.defaultProps = {
    ...TextComponent.defaultProps,
    allowFontScaling: false,
    maxFontSizeMultiplier: 1,
  };

  const TextInputComponent = TextInput as typeof TextInput & {
    defaultProps?: {
      allowFontScaling?: boolean;
      maxFontSizeMultiplier?: number;
    };
  };
  TextInputComponent.defaultProps = {
    ...TextInputComponent.defaultProps,
    allowFontScaling: false,
    maxFontSizeMultiplier: 1,
  };

  useEffect(() => {
    let cancelled = false;

    const bootstrap = async () => {
      try {
        await initDatabase();
        const session = await authStorage.load();
        if (!session) {
          if (!cancelled) setLoading(false);
          return;
        }

        if (!cancelled) {
          restoreAuth(session.user, session.accessToken, session.refreshToken);
        }

        try {
          const refreshed = await authService.refresh(session.refreshToken);
          if (!cancelled) {
            setAuth(refreshed.user, refreshed.accessToken, refreshed.refreshToken);
          }
        } catch (error) {
          const code = (error as { code?: string } | null)?.code;
          if (code === "UNAUTHORIZED" || code === "INVALID_TOKEN") {
            if (!cancelled) clearAuth();
            return;
          }
          console.warn("刷新登录状态失败，保留本地登录态：", error);
        }
      } catch (error) {
        console.error("启动初始化失败：", error);
        if (!cancelled) setLoading(false);
      }
    };

    bootstrap();
    return () => {
      cancelled = true;
    };
  }, []);

  // Launch readiness: dismiss overlay when init completes
  // - Not logged in: ready as soon as isLoading becomes false
  // - Logged in: ready after news prefetch completes (success or error)
  useEffect(() => {
    if (isLoading) return;

    if (!isAuthenticated) {
      setLaunchReady(true);
      return;
    }

    // Authenticated — trigger news prefetch once. The timeout only limits how
    // long launch waits for news; it never bypasses database/auth bootstrap.
    if (!newsPrefetchStartedRef.current) {
      newsPrefetchStartedRef.current = true;
      useNewsBootstrapStore.getState().prefetch();
    }

    const current = useNewsBootstrapStore.getState();
    if (current.status === "success" || current.status === "error") {
      setLaunchReady(true);
      return;
    }

    let finished = false;
    let timeout: ReturnType<typeof setTimeout> | null = null;
    let unsubscribe: (() => void) | null = null;

    const finishLaunchWait = () => {
      if (finished) return;
      finished = true;
      if (timeout) {
        clearTimeout(timeout);
        timeout = null;
      }
      unsubscribe?.();
      unsubscribe = null;
      setLaunchReady(true);
    };

    unsubscribe = useNewsBootstrapStore.subscribe((state) => {
      if (state.status === "success" || state.status === "error") {
        finishLaunchWait();
      }
    });
    timeout = setTimeout(finishLaunchWait, NEWS_LAUNCH_WAIT_MS);

    return () => {
      finished = true;
      if (timeout) clearTimeout(timeout);
      unsubscribe?.();
    };
  }, [isLoading, isAuthenticated]);

  useEffect(() => {
    if (isLoading) return;

    const inAuthGroup = segments[0] === "(auth)";

    if (!isAuthenticated && !inAuthGroup) {
      // Redirect to login if not authenticated and not in auth group
      router.replace("/login");
    } else if (isAuthenticated && inAuthGroup) {
      // Redirect to home if authenticated and in auth group
      router.replace("/");
    }
  }, [isAuthenticated, segments, isLoading]);

  useEffect(() => {
    if (isLoading || updateCheckStartedRef.current || !appUpdateService.isSupported) {
      return;
    }
    updateCheckStartedRef.current = true;
    void appUpdateService
      .checkForUpdate()
      .then((result) => {
        if (result.updateAvailable) {
          appUpdateService.promptForUpdate(result.release);
        }
      })
      .catch((error) => {
        console.warn("自动检查应用更新失败：", error);
      });
  }, [isLoading]);

  useEffect(() => {
    if (!isAuthenticated || !token) {
      friendRealtimeService.disconnect();
      useFriendBadgeStore.getState().reset();
      return;
    }
    friendRealtimeService.connect();
    return () => {
      friendRealtimeService.disconnect();
    };
  }, [isAuthenticated, token]);

  useEffect(() => {
    if (!isAuthenticated || !token) {
      return;
    }

    let cancelled = false;
    let syncing = false;

    const syncUnreadCount = async () => {
      if (syncing) return;
      syncing = true;
      try {
        const response = await friendService.listConversations();
        if (!cancelled && response.success) {
          useFriendBadgeStore.getState().syncFromConversations(response.data ?? []);
        }
      } catch (error) {
        console.warn("同步好友未读消息失败：", error);
      } finally {
        syncing = false;
      }
    };

    // 登录恢复后立即同步，确保无需先进入好友页也能显示未读数。
    void syncUnreadCount();

    // 实时连接是主通道，定时同步用于补偿断网、切后台等场景中遗漏的事件。
    const interval = setInterval(() => {
      if (AppState.currentState === "active") {
        void syncUnreadCount();
      }
    }, 15_000);

    const appStateSubscription = AppState.addEventListener("change", (nextState) => {
      if (nextState === "active") {
        friendRealtimeService.connect();
        void syncUnreadCount();
      } else {
        friendRealtimeService.disconnect();
      }
    });

    return () => {
      cancelled = true;
      clearInterval(interval);
      appStateSubscription.remove();
    };
  }, [isAuthenticated, token]);

  useEffect(() => {
    if (!isAuthenticated || !token) {
      return;
    }
    const unsubscribe = friendRealtimeService.subscribe({
      onMessage: (message, conversation) => {
        useFriendBadgeStore.getState().applyIncomingMessage(message, conversation);

        const isCurrentConversation =
          segments[0] === "friend-chat" && globalParams.friendUserId === message.fromUserId;
        if (AppState.currentState !== "active" || isCurrentConversation) {
          return;
        }

        if (bannerTimerRef.current) {
          clearTimeout(bannerTimerRef.current);
        }
        setMessageBanner({
          friendUserId: message.fromUserId,
          friendName: conversation?.friendDisplayName || conversation?.friendEmail || "好友消息",
          content:
            message.type === "image"
              ? "[图片消息]"
              : message.type === "audio"
                ? "[语音消息]"
                : message.content
        });
        Vibration.vibrate(20);
        bannerTimerRef.current = setTimeout(() => {
          setMessageBanner(null);
          bannerTimerRef.current = null;
        }, 4_000);
      },
      onConversationRead: (payload) => {
        if (payload.conversation) {
          useFriendBadgeStore.getState().upsertConversation(payload.conversation);
        } else {
          useFriendBadgeStore.getState().clearConversationUnread(payload.friendUserId);
        }
      },
    });
    return unsubscribe;
  }, [globalParams.friendUserId, isAuthenticated, segments[0], token]);

  useEffect(() => {
    return () => {
      if (bannerTimerRef.current) {
        clearTimeout(bannerTimerRef.current);
        bannerTimerRef.current = null;
      }
    };
  }, []);

  const openBannerConversation = () => {
    if (!messageBanner) return;
    const banner = messageBanner;
    setMessageBanner(null);
    if (bannerTimerRef.current) {
      clearTimeout(bannerTimerRef.current);
      bannerTimerRef.current = null;
    }
    router.push({
      pathname: "/friend-chat",
      params: {
        friendUserId: banner.friendUserId,
        friendName: banner.friendName
      }
    });
  };

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    let cancelled = false;
    const registerDevice = async () => {
      const nativeInfo = await nativePushService.getRegistrationInfo();
      if (cancelled) {
        return;
      }
      await deviceService.register({
        deviceName: Platform.select({
          android: "Android",
          ios: "iPhone",
          default: "Web"
        })!,
        pushToken: nativeInfo.pushToken,
        vendorDeviceId: nativeInfo.vendorDeviceId,
        pushProvider: nativeInfo.provider,
        pushEnabled: !!nativeInfo.vendorDeviceId || !!nativeInfo.pushToken,
        metadata: {
          platform: Platform.OS,
          initialized: nativeInfo.initialized
        }
      });
    };
    void registerDevice();
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  return (
    <>
      {showLaunch && (
        <AppLaunchLoading
          ready={launchReady}
          onFadeOutComplete={() => setShowLaunch(false)}
        />
      )}
      {!isLoading && (
        <>
          <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(auth)" options={{ headerShown: false }} />
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="ai" options={{ headerShown: false }} />
        <Stack.Screen name="ai-chat" options={{ headerShown: false }} />
        <Stack.Screen name="meal-upload" options={{
          headerShown: true,
          headerTitle: "饮食拍照",
          headerStyle: { backgroundColor: colors.background },
          headerShadowVisible: false,
          headerTitleStyle: { color: colors.text, fontWeight: "800" },
        }} />
        <Stack.Screen name="friend-chat" options={{
          headerShown: true,
          headerTitle: "好友互动",
          headerStyle: { backgroundColor: colors.background },
          headerShadowVisible: false,
          headerTitleStyle: { color: colors.text, fontWeight: "800" },
        }} />
        <Stack.Screen name="news-webview" options={{ headerShown: true, headerTitle: "新闻" }} />
        <Stack.Screen
          name="vocab"
          options={{
            headerShown: true,
            headerTitle: "背单词",
            headerStyle: { backgroundColor: colors.background },
            headerShadowVisible: false,
            headerTitleStyle: { color: colors.text, fontWeight: "800" },
          }}
        />
      </Stack>
      <StatusBar style="dark" />
      {messageBanner ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${messageBanner.friendName}发来新消息，点击查看`}
          onPress={openBannerConversation}
          style={[styles.messageBanner, { top: Math.max(insets.top, 10) + 6 }]}
        >
          <View style={styles.messageBannerIcon}>
            <Ionicons name="chatbubble-ellipses" size={20} color="#FFFFFF" />
          </View>
          <View style={styles.messageBannerText}>
            <Text numberOfLines={1} style={styles.messageBannerTitle}>{messageBanner.friendName}</Text>
            <Text numberOfLines={1} style={styles.messageBannerContent}>{messageBanner.content}</Text>
          </View>
          <Ionicons name="chevron-forward" size={18} color={colors.muted} />
        </Pressable>
      ) : null}
        </>
      )}
    </>
  );
}

const styles = StyleSheet.create({
  messageBanner: {
    position: "absolute",
    left: 14,
    right: 14,
    zIndex: 1000,
    elevation: 12,
    minHeight: 64,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    shadowColor: "#0F172A",
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.16,
    shadowRadius: 12
  },
  messageBannerIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.accent
  },
  messageBannerText: {
    flex: 1,
    gap: 3
  },
  messageBannerTitle: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "800"
  },
  messageBannerContent: {
    color: colors.muted,
    fontSize: 13
  }
});
