import { useEffect, useRef, useState } from "react";
import { Stack, useLocalSearchParams } from "expo-router";
import { Animated, Easing, Image, ScrollView, StyleSheet, Text, View } from "react-native";
import { WebView } from "react-native-webview";
import { Ionicons } from "@expo/vector-icons";

import { colors } from "@/theme/colors";

const FADE_OUT_MS = 240;
const MIN_PREVIEW_MS = 600;

// JS injected after page load to remove obvious ad frames and hydrate lazy images.
// Avoid hiding every fixed/sticky element because many publishers use those
// positions for legitimate navigation and article controls.
const CLEANUP_JS = `
(function() {
  try {
    var iframes = document.querySelectorAll('iframe');
    for (var i = 0; i < iframes.length; i++) {
      var src = (iframes[i].src || '').toLowerCase();
      var isAdFrame = /doubleclick|googlesyndication|adservice|(^|[./?&=_-])(ads?|advert|banner|popup)([./?&=_-]|$)/.test(src);
      if (isAdFrame) {
        iframes[i].style.display = 'none';
      }
    }
    var imgs = document.querySelectorAll('img[loading="lazy"], img[data-src]');
    for (var j = 0; j < imgs.length; j++) {
      if (imgs[j].dataset.src) {
        imgs[j].src = imgs[j].dataset.src;
      }
      imgs[j].loading = 'eager';
    }
  } catch (e) {}
})();
true;
`;

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
  const [progressBarWidth] = useState(() => new Animated.Value(0));

  const webviewLoadedRef = useRef(false);
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

  // Minimum preview display timer — avoids flash for fast-loading pages
  useEffect(() => {
    minTimerRef.current = setTimeout(() => {
      minElapsedRef.current = true;
      if (webviewLoadedRef.current) {
        dismissPreview();
      }
    }, MIN_PREVIEW_MS);

    return () => {
      if (minTimerRef.current) {
        clearTimeout(minTimerRef.current);
      }
    };
  }, []);

  const handleLoadEnd = () => {
    webviewLoadedRef.current = true;
    if (minElapsedRef.current) {
      dismissPreview();
    }
  };

  const handleLoadProgress = (e: { nativeEvent: { progress: number } }) => {
    Animated.timing(progressBarWidth, {
      toValue: e.nativeEvent.progress,
      duration: 200,
      easing: Easing.out(Easing.ease),
      useNativeDriver: false,
    }).start();
  };

  return (
    <>
      <Stack.Screen options={{ headerShown: true, headerTitle: headerTitle }} />
      <View style={styles.container}>
        <WebView
          source={{ uri: url }}
          startInLoadingState={false}
          onLoadEnd={handleLoadEnd}
          onLoadProgress={handleLoadProgress}
          injectedJavaScript={CLEANUP_JS}
          javaScriptEnabled
          domStorageEnabled
          cacheEnabled
          style={styles.webview}
        />

        {/* Top progress bar */}
        <Animated.View
          style={[
            styles.progressBar,
            {
              width: progressBarWidth.interpolate({
                inputRange: [0, 1],
                outputRange: ["0%", "100%"],
              }),
              opacity: previewOpacity.interpolate({
                inputRange: [0, 1],
                outputRange: [0, 1],
              }),
            },
          ]}
        />

        {/* Preview overlay — shows news metadata while WebView loads behind it */}
        <Animated.View style={[styles.preview, { opacity: previewOpacity }]} pointerEvents="none">
          <ScrollView contentContainerStyle={styles.previewContent} showsVerticalScrollIndicator={false}>
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
          </ScrollView>
        </Animated.View>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  webview: {
    flex: 1,
  },
  progressBar: {
    position: "absolute",
    top: 0,
    left: 0,
    height: 3,
    backgroundColor: colors.accent,
    zIndex: 10,
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
  previewContent: {
    padding: 20,
    paddingBottom: 60,
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
});
