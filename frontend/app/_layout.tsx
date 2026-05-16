import { useEffect } from "react";
import { Stack, useRouter, useSegments } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useAuthStore } from "@/store/authStore";
import { initDatabase } from "@/db/database";
import { authService } from "@/services/authService";
import { authStorage } from "@/services/authStorage";

export default function RootLayout() {
  const { isAuthenticated, isLoading, setLoading, restoreAuth, setAuth } = useAuthStore();
  const segments = useSegments();
  const router = useRouter();

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
        <Stack.Screen name="meal-upload" options={{ headerShown: true, headerTitle: "饮食拍照" }} />
        <Stack.Screen name="friend-chat" options={{ headerShown: true, headerTitle: "好友互动" }} />
        <Stack.Screen name="news-webview" options={{ headerShown: true, headerTitle: "新闻" }} />
      </Stack>
      <StatusBar style="dark" />
    </>
  );
}
