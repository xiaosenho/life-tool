import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput, Alert } from "react-native";
import { Screen } from "@/components/Screen";
import { colors } from "@/theme/colors";
import { focusService } from "@/services/focusService";
import {
  habitService,
  Habit,
  HabitCheckin,
  serverCheckinToLocal,
  serverHabitToLocal,
} from "@/services/habitService";
import { useAuthStore } from "@/store/authStore";
import { useFocusEffect } from "expo-router";

export default function TodayScreen() {
  const { isAuthenticated } = useAuthStore();
  const [focusStats, setFocusStats] = useState({ totalSeconds: 0, sessionCount: 0 });
  const [habits, setHabits] = useState<Habit[]>([]);
  const [checkins, setCheckins] = useState<HabitCheckin[]>([]);
  const [newHabitName, setNewHabitName] = useState("");
  const [offlineNotice, setOfflineNotice] = useState<string | null>(null);

  const todayKey = () => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  };

  const loadData = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      let stats;
      let usingLocalFocus = false;
      try {
        stats = await focusService.getTodayStatsFromServer();
      } catch {
        usingLocalFocus = true;
        stats = await focusService.getTodayStats();
      }

      let allHabits: Habit[];
      let todayCheckins: HabitCheckin[];
      let usingLocalHabits = false;
      try {
        const habitsRes = await habitService.getHabitsFromServer();
        if (!habitsRes.success || !habitsRes.data) {
          throw new Error(habitsRes.error?.message || "获取习惯失败");
        }
        const date = todayKey();
        allHabits = habitsRes.data.map(serverHabitToLocal);
        const checkinResults = await Promise.all(
          habitsRes.data.map((habit) => habitService.getCheckinsFromServer(habit.id, date, date))
        );
        todayCheckins = checkinResults.flatMap((res) => (
          res.success && res.data ? res.data.map(serverCheckinToLocal) : []
        ));
      } catch {
        usingLocalHabits = true;
        allHabits = await habitService.getHabits();
        todayCheckins = await habitService.getTodayCheckins();
      }
      if (usingLocalHabits) {
        setOfflineNotice("当前无法连接服务器，已显示本地习惯和打卡记录。恢复网络后请到“我的”页手动同步。");
      } else if (usingLocalFocus) {
        setOfflineNotice("当前无法连接服务器，已显示本地专注统计。恢复网络后请到“我的”页手动同步。");
      } else {
        setOfflineNotice(null);
      }

      setFocusStats(stats);
      setHabits(allHabits);
      setCheckins(todayCheckins);
    } catch (error) {
      console.error("Failed to load today data:", error);
    }
  }, [isAuthenticated]);

  useFocusEffect(
    useCallback(() => {
      loadData();
    }, [loadData])
  );

  const handleCreateHabit = async () => {
    if (!newHabitName.trim()) return;
    try {
      const payload = {
        name: newHabitName.trim(),
        description: null,
        frequencyType: "daily",
        frequencyDays: null,
        targetCount: 1,
        color: colors.accent,
        icon: "star",
      };
      const response = await habitService.createHabitOnServer(payload);
      if (!response.success) {
        throw new Error(response.error?.message || "创建习惯失败");
      }
      setOfflineNotice(null);
      setNewHabitName("");
      loadData();
    } catch (error) {
      try {
        await habitService.createHabit({
          name: newHabitName.trim(),
          description: null,
          frequency_type: "daily",
          frequency_days: null,
          target_count: 1,
          color: colors.accent,
          icon: "star",
        });
        setNewHabitName("");
        setOfflineNotice("当前无法连接服务器，新习惯已保存到本地。恢复网络后请到“我的”页手动同步。");
        Alert.alert("已离线保存", "新习惯已保存到本地。恢复网络后请到“我的”页点击立即同步。");
        loadData();
      } catch {
        Alert.alert("错误", error instanceof Error ? error.message : "创建习惯失败");
      }
    }
  };

  const handleCheckin = async (habitId: string) => {
    const isChecked = checkins.some(c => c.habit_id === habitId);
    if (isChecked) return;

    try {
      const response = await habitService.checkinOnServer(habitId);
      if (!response.success) {
        throw new Error(response.error?.message || "打卡失败");
      }
      setOfflineNotice(null);
      loadData();
    } catch (error) {
      try {
        await habitService.checkin(habitId);
        setOfflineNotice("当前无法连接服务器，本次打卡已保存到本地。恢复网络后请到“我的”页手动同步。");
        Alert.alert("已离线保存", "本次打卡已保存到本地。恢复网络后请到“我的”页点击立即同步。");
        loadData();
      } catch {
        Alert.alert("错误", error instanceof Error ? error.message : "打卡失败");
      }
    }
  };

  const formatFocusTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    return `${mins} 分钟`;
  };

  const completedHabitsCount = habits.filter(h =>
    checkins.some(c => c.habit_id === h.id)
  ).length;

  return (
    <Screen title="今日">
      <ScrollView contentContainerStyle={styles.container}>
        {offlineNotice && (
          <View style={styles.offlineBanner}>
            <Text style={styles.offlineTitle}>离线模式</Text>
            <Text style={styles.offlineText}>{offlineNotice}</Text>
          </View>
        )}

        <View style={styles.statsCard}>
          <Text style={styles.cardTitle}>今日概览</Text>
          <View style={styles.statsRow}>
            <View style={styles.statItem}>
              <Text style={styles.statLabel}>专注时长</Text>
              <Text style={styles.statValue}>{formatFocusTime(focusStats.totalSeconds)}</Text>
            </View>
            <View style={styles.statItem}>
              <Text style={styles.statLabel}>习惯完成</Text>
              <Text style={styles.statValue}>{completedHabitsCount} / {habits.length}</Text>
            </View>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>我的习惯</Text>
          {habits.length === 0 ? (
            <Text style={styles.emptyText}>还没有习惯，开始创建一个吧！</Text>
          ) : (
            habits.map((habit) => {
              const isChecked = checkins.some(c => c.habit_id === habit.id);
              return (
                <TouchableOpacity
                  key={habit.id}
                  style={[styles.habitItem, isChecked && styles.habitItemChecked]}
                  onPress={() => handleCheckin(habit.id)}
                  disabled={isChecked}
                >
                  <View style={styles.habitInfo}>
                    <Text style={[styles.habitName, isChecked && styles.habitTextChecked]}>
                      {habit.name}
                    </Text>
                  </View>
                  <View style={[styles.checkbox, isChecked && styles.checkboxChecked]}>
                    {isChecked && <Text style={styles.checkIcon}>✓</Text>}
                  </View>
                </TouchableOpacity>
              );
            })
          )}
        </View>

        <View style={styles.addHabit}>
          <TextInput
            style={styles.input}
            placeholder="新习惯名称..."
            value={newHabitName}
            onChangeText={setNewHabitName}
          />
          <TouchableOpacity style={styles.addButton} onPress={handleCreateHabit}>
            <Text style={styles.addButtonText}>添加</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  statsCard: {
    backgroundColor: colors.surface,
    padding: 20,
    borderRadius: 16,
    marginBottom: 24,
    elevation: 3,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: "bold",
    color: colors.text,
    marginBottom: 16,
  },
  statsRow: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  statItem: {
    flex: 1,
  },
  statLabel: {
    fontSize: 12,
    color: colors.muted,
    marginBottom: 4,
  },
  statValue: {
    fontSize: 20,
    fontWeight: "bold",
    color: colors.accent,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "bold",
    color: colors.text,
    marginBottom: 12,
  },
  emptyText: {
    textAlign: "center",
    color: colors.muted,
    marginTop: 20,
    fontSize: 14,
  },
  habitItem: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.surface,
    padding: 16,
    borderRadius: 12,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: colors.border,
  },
  habitItemChecked: {
    backgroundColor: "#F1F5F9",
    borderColor: "transparent",
  },
  habitInfo: {
    flex: 1,
  },
  habitName: {
    fontSize: 16,
    color: colors.text,
    fontWeight: "500",
  },
  habitTextChecked: {
    color: colors.muted,
    textDecorationLine: "line-through",
  },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: colors.accent,
    justifyContent: "center",
    alignItems: "center",
  },
  checkboxChecked: {
    backgroundColor: colors.accent,
    borderColor: colors.accent,
  },
  checkIcon: {
    color: colors.surface,
    fontSize: 14,
    fontWeight: "bold",
  },
  addHabit: {
    flexDirection: "row",
    alignItems: "center",
    marginTop: 10,
    gap: 10,
  },
  input: {
    flex: 1,
    backgroundColor: colors.surface,
    padding: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.border,
  },
  offlineBanner: {
    backgroundColor: "#FFF7ED",
    borderColor: "#FDBA74",
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: 16,
    padding: 12,
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
  addButton: {
    backgroundColor: colors.accent,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 10,
  },
  addButtonText: {
    color: colors.surface,
    fontWeight: "bold",
  },
});
