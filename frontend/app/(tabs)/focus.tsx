import React, { useState, useEffect, useRef } from "react";
import { View, Text, StyleSheet, TouchableOpacity, Alert } from "react-native";
import { Screen } from "@/components/Screen";
import { colors } from "@/theme/colors";
import { focusService } from "@/services/focusService";

const INITIAL_TIME = 25 * 60;

export default function FocusScreen() {
  const [timeLeft, setTimeLeft] = useState(INITIAL_TIME);
  const [isActive, setIsActive] = useState(false);
  const [todayStats, setTodayStats] = useState({ totalSeconds: 0, sessionCount: 0 });
  const [startedAt, setStartedAt] = useState<string | null>(null);

  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    loadTodayStats();
  }, []);

  useEffect(() => {
    if (isActive && timeLeft > 0) {
      timerRef.current = setInterval(() => {
        setTimeLeft((prev) => prev - 1);
      }, 1000);
    } else if (timeLeft === 0) {
      handleComplete();
    } else {
      if (timerRef.current) clearInterval(timerRef.current);
    }

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isActive, timeLeft]);

  const loadTodayStats = async () => {
    const stats = await focusService.getTodayStats();
    setTodayStats(stats);
  };

  const toggleTimer = () => {
    if (!isActive && !startedAt) {
      setStartedAt(new Date().toISOString());
    }
    setIsActive(!isActive);
  };

  const resetTimer = () => {
    setIsActive(false);
    setTimeLeft(INITIAL_TIME);
    setStartedAt(null);
  };

  const handleComplete = async () => {
    setIsActive(false);
    const actualSeconds = INITIAL_TIME - timeLeft;
    if (actualSeconds < 10) {
      Alert.alert("提示", "专注时间太短，将不会记录。");
      resetTimer();
      return;
    }

    try {
      await focusService.saveSession({
        mode: "pomodoro",
        target_seconds: INITIAL_TIME,
        actual_seconds: actualSeconds,
        status: "completed",
        started_at: startedAt || new Date().toISOString(),
        ended_at: new Date().toISOString(),
        note: null,
      });
      Alert.alert("好样的！", timeLeft === 0 ? "你完成了一次专注！" : "专注已保存。");
      loadTodayStats();
      resetTimer();
    } catch (error) {
      Alert.alert("错误", "保存失败，请稍后重试。");
    }
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    return `${mins} 分钟`;
  };

  return (
    <Screen title="专注">
      <View style={styles.container}>
        <View style={styles.statsRow}>
          <View style={styles.statBox}>
            <Text style={styles.statLabel}>今日专注</Text>
            <Text style={styles.statValue}>{formatDuration(todayStats.totalSeconds)}</Text>
          </View>
          <View style={styles.statBox}>
            <Text style={styles.statLabel}>完成次数</Text>
            <Text style={styles.statValue}>{todayStats.sessionCount} 次</Text>
          </View>
        </View>

        <View style={styles.timerContainer}>
          <Text style={styles.timerText}>{formatTime(timeLeft)}</Text>
          <Text style={styles.modeText}>番茄钟 (25分钟)</Text>
        </View>

        <View style={styles.controls}>
          <TouchableOpacity
            style={[styles.button, isActive ? styles.pauseButton : styles.startButton]}
            onPress={toggleTimer}
          >
            <Text style={styles.buttonText}>{isActive ? "暂停" : "开始"}</Text>
          </TouchableOpacity>

          {startedAt && (
            <TouchableOpacity style={[styles.button, styles.finishButton]} onPress={handleComplete}>
              <Text style={styles.buttonText}>完成</Text>
            </TouchableOpacity>
          )}

          <TouchableOpacity style={[styles.button, styles.resetButton]} onPress={resetTimer}>
            <Text style={styles.buttonText}>重置</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    alignItems: "center",
  },
  statsRow: {
    flexDirection: "row",
    justifyContent: "space-around",
    width: "100%",
    marginBottom: 40,
  },
  statBox: {
    alignItems: "center",
    backgroundColor: colors.surface,
    padding: 15,
    borderRadius: 12,
    width: "45%",
    elevation: 2,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  statLabel: {
    fontSize: 14,
    color: colors.muted,
    marginBottom: 5,
  },
  statValue: {
    fontSize: 18,
    fontWeight: "bold",
    color: colors.text,
  },
  timerContainer: {
    width: 250,
    height: 250,
    borderRadius: 125,
    borderWidth: 10,
    borderColor: colors.accent,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 40,
  },
  timerText: {
    fontSize: 60,
    fontWeight: "bold",
    color: colors.text,
  },
  modeText: {
    fontSize: 16,
    color: colors.muted,
    marginTop: 10,
  },
  controls: {
    flexDirection: "row",
    justifyContent: "center",
    gap: 15,
  },
  button: {
    paddingHorizontal: 30,
    paddingVertical: 12,
    borderRadius: 25,
    minWidth: 100,
    alignItems: "center",
  },
  buttonText: {
    color: colors.surface,
    fontSize: 16,
    fontWeight: "600",
  },
  startButton: {
    backgroundColor: colors.accent,
  },
  pauseButton: {
    backgroundColor: "#F59E0B",
  },
  finishButton: {
    backgroundColor: colors.text,
  },
  resetButton: {
    backgroundColor: colors.muted,
  },
});
