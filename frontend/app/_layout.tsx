import { useEffect } from "react";
import { Platform, Text, TextInput } from "react-native";
import { Stack, useRouter, useSegments } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useAuthStore } from "@/store/authStore";
import { initDatabase } from "@/db/database";
import { authService } from "@/services/authService";
import { authStorage } from "@/services/authStorage";
import { deviceService } from "@/services/deviceService";
import { nativePushService } from "@/services/nativePushService";
import { friendRealtimeService } from "@/services/friendRealtimeService";
import { colors } from "@/theme/colors";
import { useFriendBadgeStore } from "@/store/friendBadgeStore";

export default function RootLayout() {
  const { isAuthenticated, isLoading, setLoading, restoreAuth, setAuth, clearAuth, token } = useAuthStore();
  const segments = useSegments();
  const router = useRouter();

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
    if (!isAuthenticated || !token) {
      friendRealtimeService.disconnect();
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
    const unsubscribe = friendRealtimeService.subscribe({
      onMessage: (message, conversation) => {
        useFriendBadgeStore.getState().applyIncomingMessage(message, conversation);
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
  }, [isAuthenticated, token]);

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

  if (isLoading) {
    return null;
  }

  return (
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
    </>
  );
}
