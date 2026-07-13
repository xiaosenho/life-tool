import { useEffect, useRef, useState } from "react";
import { Stack, useLocalSearchParams } from "expo-router";
import { Animated, Easing, Image, Linking, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { colors } from "@/theme/colors";

const FADE_OUT_MS = 240;
const MIN_PREVIEW_MS = 600;

export default function NewsWebViewScreen() {
  const params = useLocalSearchParams<{
    url?: string;
    title?: string;
    newsTitle?: string;
    summary?: string;
    imageUrl?: string;
  }>();
  const url = typeof params.url === "string" ? params.url : "";
  const headerTitle = typeof params.title === "string" ? params.title : "新闻";
  const newsTitle = typeof params.newsTitle === "string" ? params.newsTitle : "";
  const summary = typeof params.summary === "string" ? params.summary : "";
  const imageUrl = typeof params.imageUrl === "string" && params.imageUrl ? params.imageUrl : null;

  const [previewOpacity] = useState(() => new Animated.Value(1));
  const iframeLoadedRef = useRef(false);
  const minElapsedRef = useRef(false);
  const fadeStartedRef = useRef(false);
  const minTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const dismissPreview = () => {
    if (fadeStartedRef.current) return;
    fadeStartedRef.current = true;

    Animated.timing(previewOpacity, {
      toValue: 0,
      duration: FADE_OUT_MS,
      easing: Easing.out(Easing.ease),
      useNativeDriver: true,
    }).start();
  };

  useEffect(() => {
    minTimerRef.current = setTimeout(() => {
      minElapsedRef.current = true;
      if (iframeLoadedRef.current) {
        dismissPreview();
      }
    }, MIN_PREVIEW_MS);

    return () => {
      if (minTimerRef.current) {
        clearTimeout(minTimerRef.current);
      }
    };
  }, []);

  const handleIframeLoad = () => {
    iframeLoadedRef.current = true;
    if (minElapsedRef.current) {
      dismissPreview();
    }
  };

  return (
    <>
      <Stack.Screen options={{ headerShown: true, headerTitle: headerTitle }} />
      <View style={styles.container}>
        {url ? (
          <iframe
            src={url}
            title={headerTitle}
            style={styles.iframe}
            onLoad={handleIframeLoad}
          />
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyTitle}>新闻地址为空</Text>
            <Text style={styles.emptyDescription}>请返回新闻列表重新打开。</Text>
          </View>
        )}

        {/* Preview overlay */}
        {url ? (
          <Animated.View style={[styles.preview, { opacity: previewOpacity }]} pointerEvents="none">
            <View style={styles.previewScroll}>
              {imageUrl ? (
                <View style={styles.imageWrapper}>
                  <View style={styles.imagePlaceholder}>
                    <Ionicons name="image-outline" size={32} color={colors.muted} />
                  </View>
                  <Image source={{ uri: imageUrl }} style={styles.previewImage} resizeMode="cover" />
                </View>
              ) : null}

              {newsTitle ? <Text style={styles.previewTitle}>{newsTitle}</Text> : null}

              <Text style={styles.previewSource}>{headerTitle}</Text>

              {summary ? <Text style={styles.previewSummary}>{summary}</Text> : null}

              <View style={styles.loadingHint}>
                <View style={styles.loadingDot} />
                <Text style={styles.loadingText}>正在加载完整内容</Text>
              </View>
            </View>
          </Animated.View>
        ) : null}

        {url ? (
          <Pressable style={styles.openButton} onPress={() => Linking.openURL(url)}>
            <Text style={styles.openButtonText}>在新窗口打开</Text>
          </Pressable>
        ) : null}
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  iframe: {
    borderWidth: 0,
    flex: 1,
    width: "100%",
  },
  emptyDescription: {
    color: colors.muted,
    fontSize: 14,
    marginTop: 8,
  },
  emptyState: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center",
    padding: 24,
  },
  emptyTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "700",
  },
  preview: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: colors.background,
    zIndex: 5,
  },
  previewScroll: {
    padding: 20,
    paddingBottom: 60,
    maxWidth: 680,
    marginHorizontal: "auto",
  },
  imageWrapper: {
    width: "100%",
    height: 180,
    borderRadius: 12,
    marginBottom: 16,
    overflow: "hidden",
    backgroundColor: colors.border,
  },
  imagePlaceholder: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.border,
  },
  previewImage: {
    ...StyleSheet.absoluteFillObject,
    width: "100%",
    height: "100%",
  },
  previewTitle: {
    fontSize: 20,
    fontWeight: "800",
    color: colors.text,
    lineHeight: 28,
    marginBottom: 8,
  },
  previewSource: {
    fontSize: 13,
    color: colors.muted,
    marginBottom: 16,
  },
  previewSummary: {
    fontSize: 15,
    color: colors.text,
    lineHeight: 24,
    opacity: 0.85,
  },
  loadingHint: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginTop: 32,
    justifyContent: "center",
  },
  loadingDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.accent,
  },
  loadingText: {
    fontSize: 13,
    color: colors.muted,
  },
  openButton: {
    alignSelf: "center",
    backgroundColor: colors.accent,
    borderRadius: 999,
    bottom: 24,
    paddingHorizontal: 18,
    paddingVertical: 10,
    position: "absolute",
    right: 24,
    zIndex: 20,
  },
  openButtonText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "700",
  },
});
