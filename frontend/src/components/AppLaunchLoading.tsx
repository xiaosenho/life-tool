import React, { useEffect, useRef } from "react";
import { Animated, Easing, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/theme/colors";
import { useSafeAreaInsets } from "react-native-safe-area-context";

const MIN_DURATION_MS = 900;
const FADE_OUT_DURATION_MS = 220;

interface Props {
  ready: boolean;
  onFadeOutComplete: () => void;
}

export function AppLaunchLoading({ ready, onFadeOutComplete }: Props) {
  const insets = useSafeAreaInsets();
  const opacity = useRef(new Animated.Value(1)).current;
  const breathe = useRef(new Animated.Value(1)).current;
  const dotScale1 = useRef(new Animated.Value(1)).current;
  const dotScale2 = useRef(new Animated.Value(1)).current;
  const dotScale3 = useRef(new Animated.Value(1)).current;
  const fadeOutStarted = useRef(false);
  const minElapsedRef = useRef(false);
  const minTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);
  const readyRef = useRef(ready);

  // Keep readyRef in sync so tryFadeOut can read latest value
  useEffect(() => {
    readyRef.current = ready;
  }, [ready]);

  // Breathing circle animation loop
  useEffect(() => {
    const breatheAnim = Animated.loop(
      Animated.sequence([
        Animated.timing(breathe, {
          toValue: 1.12,
          duration: 1000,
          easing: Easing.inOut(Easing.sin),
          useNativeDriver: true,
        }),
        Animated.timing(breathe, {
          toValue: 1,
          duration: 1000,
          easing: Easing.inOut(Easing.sin),
          useNativeDriver: true,
        }),
      ])
    );
    breatheAnim.start();
    return () => {
      breatheAnim.stop();
    };
  }, [breathe]);

  // Three-dot pulse animation
  useEffect(() => {
    const createPulse = (dot: Animated.Value, delay: number) =>
      Animated.loop(
        Animated.sequence([
          Animated.delay(delay),
          Animated.timing(dot, {
            toValue: 1.4,
            duration: 400,
            easing: Easing.inOut(Easing.ease),
            useNativeDriver: true,
          }),
          Animated.timing(dot, {
            toValue: 1,
            duration: 400,
            easing: Easing.inOut(Easing.ease),
            useNativeDriver: true,
          }),
        ])
      );

    const a1 = createPulse(dotScale1, 0);
    const a2 = createPulse(dotScale2, 200);
    const a3 = createPulse(dotScale3, 400);
    a1.start();
    a2.start();
    a3.start();
    return () => {
      a1.stop();
      a2.stop();
      a3.stop();
    };
  }, [dotScale1, dotScale2, dotScale3]);

  // Minimum display timer
  useEffect(() => {
    minTimerRef.current = setTimeout(() => {
      if (!mountedRef.current) return;
      minElapsedRef.current = true;
      minTimerRef.current = null;
      tryFadeOut();
    }, MIN_DURATION_MS);
    return () => {
      if (minTimerRef.current) {
        clearTimeout(minTimerRef.current);
        minTimerRef.current = null;
      }
    };
  }, []);

  // React to ready prop
  useEffect(() => {
    if (ready) {
      tryFadeOut();
    }
  }, [ready]);

  const tryFadeOut = () => {
    if (fadeOutStarted.current) return;
    if (!minElapsedRef.current || !readyRef.current) return;

    fadeOutStarted.current = true;

    if (minTimerRef.current) {
      clearTimeout(minTimerRef.current);
      minTimerRef.current = null;
    }

    Animated.timing(opacity, {
      toValue: 0,
      duration: FADE_OUT_DURATION_MS,
      easing: Easing.out(Easing.ease),
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished && mountedRef.current) {
        onFadeOutComplete();
      }
    });
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      mountedRef.current = false;
      if (minTimerRef.current) {
        clearTimeout(minTimerRef.current);
      }
    };
  }, []);

  return (
    <Animated.View
      style={[
        styles.overlay,
        {
          paddingTop: insets.top,
          paddingBottom: insets.bottom,
          paddingLeft: insets.left,
          paddingRight: insets.right,
          opacity,
        },
      ]}
      pointerEvents="none"
    >
      <View style={styles.content}>
        <Animated.View style={{ transform: [{ scale: breathe }] }}>
          <View style={styles.iconContainer}>
            <Ionicons name="sparkles" size={40} color={colors.accent} />
          </View>
        </Animated.View>

        <Text style={styles.appName}>LifeTool</Text>
        <Text style={styles.tagline}>整理今天，准备出发</Text>

        <View style={styles.dotsRow}>
          <Animated.View
            style={[
              styles.dot,
              { backgroundColor: colors.accent, transform: [{ scale: dotScale1 }] },
            ]}
          />
          <Animated.View
            style={[
              styles.dot,
              { backgroundColor: colors.accent, transform: [{ scale: dotScale2 }] },
            ]}
          />
          <Animated.View
            style={[
              styles.dot,
              { backgroundColor: colors.accent, transform: [{ scale: dotScale3 }] },
            ]}
          />
        </View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: colors.background,
    justifyContent: "center",
    alignItems: "center",
    zIndex: 9999,
    elevation: 9999,
  },
  content: {
    alignItems: "center",
    justifyContent: "center",
    maxWidth: 320,
  },
  iconContainer: {
    width: 72,
    height: 72,
    borderRadius: 24,
    backgroundColor: colors.surface,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 24,
    borderWidth: 1,
    borderColor: colors.border,
  },
  appName: {
    fontSize: 24,
    fontWeight: "800",
    color: colors.text,
    marginBottom: 8,
    textAlign: "center",
  },
  tagline: {
    fontSize: 14,
    color: colors.muted,
    marginBottom: 20,
    textAlign: "center",
  },
  dotsRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
});
