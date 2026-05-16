import React, { useState, useEffect, useRef } from "react";
import { router } from "expo-router";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  TextInput,
  ScrollView,
} from "react-native";
import { Screen } from "@/components/Screen";
import { colors } from "@/theme/colors";
import { focusService } from "@/services/focusService";

const PRESET_MINUTES = [15, 25, 45, 60];
const DEFAULT_MINUTES = 25;

export default function FocusScreen() {
  const [timeLeft, setTimeLeft] = useState(DEFAULT_MINUTES * 60);
  const [targetMinutes, setTargetMinutes] = useState(DEFAULT_MINUTES);
  const [defaultMinutes, setDefaultMinutes] = useState(DEFAULT_MINUTES);
  const [customMinutes, setCustomMinutes] = useState(String(DEFAULT_MINUTES));
  const [isActive, setIsActive] = useState(false);
  const [todayStats, setTodayStats] = useState({ totalSeconds: 0, sessionCount: 0 });
  const [startedAt, setStartedAt] = useState<string | null>(null);
  const [serverSessionId, setServerSessionId] = useState<string | null>(null);
  const [offlineNotice, setOfflineNotice] = useState<string | null>(null);

  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    loadPreference();
    loadTodayStats();
  }, []);

  useEffect(() => {
    if (isActive && timeLeft > 0) {
      timerRef.current = setInterval(() => {
        setTimeLeft((prev) => Math.max(prev - 1, 0));
      }, 1000);
    } else if (isActive && timeLeft === 0) {
      handleComplete();
    } else {
      if (timerRef.current) clearInterval(timerRef.current);
    }

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isActive, timeLeft]);

  const loadPreference = async () => {
    try {
      const res = await focusService.getPreferenceFromServer();
      if (res.success && res.data) {
        const mins = (res.data as any).defaultFocusMinutes || 25;
        setDefaultMinutes(mins);
        setTargetMinutes(mins);
        setCustomMinutes(String(mins));
        setTimeLeft(mins * 60);
        return;
      }
    } catch {}
    const preference = await focusService.getPreference();
    setDefaultMinutes(preference.default_focus_minutes);
    setTargetMinutes(preference.default_focus_minutes);
    setCustomMinutes(String(preference.default_focus_minutes));
    setTimeLeft(preference.default_focus_minutes * 60);
  };

  const loadTodayStats = async () => {
    try {
      const stats = await focusService.getTodayStatsFromServer();
      setTodayStats(stats);
      setOfflineNotice(null);
      return;
    } catch {}
    const localStats = await focusService.getTodayStats();
    setTodayStats(localStats);
    setOfflineNotice("当前无法连接服务器，已显示本地专注记录。恢复网络后请到“我的”页手动同步。");
  };

  const selectMinutes = (minutes: number) => {
    if (startedAt) return;
    setTargetMinutes(minutes);
    setCustomMinutes(String(minutes));
    setTimeLeft(minutes * 60);
  };

  const applyCustomMinutes = () => {
    const minutes = Number(customMinutes);
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > 180) {
      Alert.alert("提示", "专注时长需在 1-180 分钟之间。");
      setCustomMinutes(String(targetMinutes));
      return;
    }
    selectMinutes(minutes);
  };

  const saveDefaultMinutes = async () => {
    try {
      const response = await focusService.savePreferenceToServer({
        defaultFocusMinutes: targetMinutes,
      });
      if (!response.success) {
        throw new Error(response.error?.message || "保存默认时长失败");
      }
      setDefaultMinutes(targetMinutes);
      setOfflineNotice(null);
      Alert.alert("已保存", "下次进入专注页会使用这个默认时长。");
    } catch (error) {
      try {
        await focusService.savePreference({
          defaultFocusMinutes: targetMinutes,
        });
        setDefaultMinutes(targetMinutes);
        setOfflineNotice("当前无法连接服务器，默认时长已保存到本地。恢复网络后请到“我的”页手动同步。");
        Alert.alert("已离线保存", "当前可能没有网络，默认时长已保存到本地。恢复网络后请到“我的”页点击立即同步。");
      } catch {
        Alert.alert("保存失败", error instanceof Error ? error.message : "请稍后重试。");
      }
    }
  };

  const toggleTimer = async () => {
    if (!isActive && !startedAt) {
      const now = new Date().toISOString();
      setStartedAt(now);
      setIsActive(true);
      try {
        const response = await focusService.startSession("pomodoro", targetMinutes, null);
        if (response.success && response.data) {
          setServerSessionId(response.data.id);
        }
      } catch {
        setServerSessionId(null);
      }
      return;
    }
    setIsActive(!isActive);
  };

  const resetTimer = () => {
    setIsActive(false);
    setTimeLeft(targetMinutes * 60);
    setStartedAt(null);
    setServerSessionId(null);
  };

  const handleComplete = async () => {
    setIsActive(false);
    const targetSeconds = targetMinutes * 60;
    const actualSeconds = Math.max(targetSeconds - timeLeft, 0);
    if (actualSeconds < 10) {
      Alert.alert("提示", "专注时间太短，将不会记录。");
      resetTimer();
      return;
    }

    let savedToServer = false;
    const actualMinutes = Math.max(1, Math.round(actualSeconds / 60));

    try {
      let sessionId = serverSessionId;
      if (!sessionId) {
        const startRes = await focusService.startSession("pomodoro", targetMinutes, null);
        sessionId = startRes.success && startRes.data ? startRes.data.id : null;
      }
      if (sessionId) {
        const endRes = await focusService.endSession(sessionId, actualMinutes, "completed", null);
        savedToServer = endRes.success;
      }
    } catch {
      savedToServer = false;
    }

    try {
      if (savedToServer) {
        setOfflineNotice(null);
        Alert.alert("好样的！", timeLeft === 0 ? "你完成了一次专注！" : "专注已保存到服务器。");
        loadTodayStats();
        resetTimer();
        return;
      }

      await focusService.saveSession({
        mode: "pomodoro",
        target_seconds: targetSeconds,
        actual_seconds: actualSeconds,
        status: "completed",
        started_at: startedAt || new Date().toISOString(),
        ended_at: new Date().toISOString(),
        note: null,
      });
      setOfflineNotice("当前无法连接服务器，本次专注已保存到本地。恢复网络后请到“我的”页手动同步。");
      Alert.alert("已离线保存", "本次专注已保存到本地。恢复网络后请到“我的”页点击立即同步。");
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

  const canEditTarget = !startedAt;

  return (
    <Screen title="专注">
      <ScrollView contentContainerStyle={styles.container}>
        {offlineNotice && (
          <View style={styles.offlineBanner}>
            <Text style={styles.offlineTitle}>离线模式</Text>
            <Text style={styles.offlineText}>{offlineNotice}</Text>
          </View>
        )}

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

        <TouchableOpacity
          activeOpacity={0.9}
          style={[styles.timerContainer, isActive && styles.timerContainerActive]}
          onPress={toggleTimer}
        >
          <Text style={styles.timerText}>{formatTime(timeLeft)}</Text>
          <Text style={styles.modeText}>番茄钟 · {targetMinutes} 分钟</Text>
          <Text style={styles.timerHint}>{isActive ? "轻触暂停" : "轻触开始"}</Text>
        </TouchableOpacity>

        <View style={styles.controls}>
          {startedAt && (
            <TouchableOpacity style={[styles.button, styles.finishButton]} onPress={handleComplete}>
              <Text style={styles.buttonText}>完成</Text>
            </TouchableOpacity>
          )}

          <TouchableOpacity style={[styles.button, styles.resetButton]} onPress={resetTimer}>
            <Text style={styles.buttonText}>重置</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.preferencePanel}>
          <View style={styles.preferenceHeader}>
            <View>
              <Text style={styles.sectionTitle}>本次时长</Text>
              <Text style={styles.sectionHint}>默认 {defaultMinutes} 分钟</Text>
            </View>
            <TouchableOpacity
              style={[
                styles.saveDefaultButton,
                targetMinutes === defaultMinutes && styles.disabledButton,
              ]}
              onPress={saveDefaultMinutes}
              disabled={targetMinutes === defaultMinutes}
            >
              <Text style={styles.saveDefaultText}>设为默认</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.presets}>
            {PRESET_MINUTES.map((minutes) => (
              <TouchableOpacity
                key={minutes}
                style={[
                  styles.presetButton,
                  targetMinutes === minutes && styles.presetButtonActive,
                  !canEditTarget && styles.disabledButton,
                ]}
                onPress={() => selectMinutes(minutes)}
                disabled={!canEditTarget}
              >
                <Text
                  style={[
                    styles.presetText,
                    targetMinutes === minutes && styles.presetTextActive,
                  ]}
                >
                  {minutes} 分钟
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          <View style={styles.customRow}>
            <TextInput
              style={[styles.customInput, !canEditTarget && styles.disabledInput]}
              value={customMinutes}
              onChangeText={setCustomMinutes}
              onBlur={applyCustomMinutes}
              onSubmitEditing={applyCustomMinutes}
              keyboardType="number-pad"
              editable={canEditTarget}
              maxLength={3}
            />
            <Text style={styles.customUnit}>分钟</Text>
            <TouchableOpacity
              style={[styles.applyButton, !canEditTarget && styles.disabledButton]}
              onPress={applyCustomMinutes}
              disabled={!canEditTarget}
            >
              <Text style={styles.applyButtonText}>应用</Text>
            </TouchableOpacity>
          </View>

          {!canEditTarget && (
            <Text style={styles.lockHint}>计时开始后，本次目标时长会锁定。</Text>
          )}
        </View>

        <TouchableOpacity style={styles.vocabCard} onPress={() => router.push('/vocab')}>
          <View style={styles.vocabCardTextWrap}>
            <Text style={styles.vocabCardTitle}>背单词 · 今日30词</Text>
            <Text style={styles.vocabCardSubtitle}>像英语书一样顺序背，支持一键隐藏中文</Text>
          </View>
          <Text style={styles.vocabCardAction}>开始</Text>
        </TouchableOpacity>

      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    paddingHorizontal: 16,
    paddingTop: 12,
    alignItems: "center",
    paddingBottom: 24,
  },
  statsRow: {
    flexDirection: "row",
    justifyContent: "space-around",
    width: "100%",
    gap: 10,
    marginBottom: 18,
  },
  statBox: {
    alignItems: "center",
    backgroundColor: colors.surface,
    paddingVertical: 12,
    paddingHorizontal: 10,
    borderRadius: 12,
    flex: 1,
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
    width: 220,
    height: 220,
    borderRadius: 110,
    borderWidth: 10,
    borderColor: colors.accent,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 18,
    backgroundColor: colors.surface,
    paddingHorizontal: 20,
  },
  timerContainerActive: {
    borderColor: "#F59E0B",
  },
  timerText: {
    fontSize: 52,
    fontWeight: "bold",
    color: colors.text,
  },
  modeText: {
    fontSize: 15,
    color: colors.muted,
    marginTop: 8,
  },
  timerHint: {
    fontSize: 13,
    color: colors.accent,
    marginTop: 8,
    fontWeight: "600",
  },
  preferencePanel: {
    width: "100%",
    backgroundColor: colors.surface,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    marginBottom: 12,
  },
  preferenceHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: "700",
    color: colors.text,
  },
  sectionHint: {
    fontSize: 13,
    color: colors.muted,
    marginTop: 3,
  },
  saveDefaultButton: {
    borderWidth: 1,
    borderColor: colors.accent,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  saveDefaultText: {
    color: colors.accent,
    fontSize: 14,
    fontWeight: "600",
  },
  presets: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    rowGap: 8,
    marginBottom: 12,
    width: "100%",
  },
  presetButton: {
    width: "48%",
    minWidth: 0,
    paddingHorizontal: 10,
    paddingVertical: 9,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
  },
  presetButtonActive: {
    backgroundColor: colors.accent,
    borderColor: colors.accent,
  },
  presetText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "600",
  },
  presetTextActive: {
    color: colors.surface,
  },
  customRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    flexWrap: "wrap",
  },
  customInput: {
    width: 82,
    minHeight: 40,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    paddingHorizontal: 12,
    fontSize: 16,
    color: colors.text,
    backgroundColor: colors.surface,
  },
  disabledInput: {
    backgroundColor: colors.background,
    color: colors.muted,
  },
  customUnit: {
    fontSize: 15,
    color: colors.muted,
  },
  applyButton: {
    minHeight: 40,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: colors.text,
    justifyContent: "center",
    alignItems: "center",
  },
  applyButtonText: {
    color: colors.surface,
    fontSize: 14,
    fontWeight: "600",
  },
  disabledButton: {
    opacity: 0.45,
  },
  lockHint: {
    color: colors.muted,
    fontSize: 13,
    marginTop: 10,
  },
  vocabCard: {
    width: "100%",
    backgroundColor: colors.surface,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 14,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 12,
  },
  vocabCardTextWrap: {
    flex: 1,
  },
  vocabCardTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.text,
  },
  vocabCardSubtitle: {
    marginTop: 4,
    fontSize: 13,
    color: colors.muted,
    lineHeight: 18,
  },
  vocabCardAction: {
    color: colors.accent,
    fontSize: 15,
    fontWeight: "700",
  },
  controls: {
    flexDirection: "row",
    justifyContent: "center",
    flexWrap: "wrap",
    gap: 10,
    marginBottom: 14,
  },
  offlineBanner: {
    backgroundColor: "#FFF7ED",
    borderColor: "#FDBA74",
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: 16,
    padding: 12,
    width: "100%",
  },
  offlineText: {
    color: "#9A3412",
    fontSize: 13,
    lineHeight: 18,
  },
  offlineTitle: {
    color: "#9A3412",
    fontSize: 14,
    fontWeight: "700",
    marginBottom: 4,
  },
  button: {
    paddingHorizontal: 22,
    paddingVertical: 10,
    borderRadius: 999,
    minWidth: 88,
    alignItems: "center",
  },
  buttonText: {
    color: colors.surface,
    fontSize: 16,
    fontWeight: "600",
  },
  finishButton: {
    backgroundColor: colors.text,
  },
  resetButton: {
    backgroundColor: colors.muted,
  },
});
