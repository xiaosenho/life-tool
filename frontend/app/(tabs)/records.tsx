import { useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { usePathname, useRouter } from "expo-router";
import { MaterialCommunityIcons } from "@expo/vector-icons";

import { DateInput } from "@/components/DateInput";
import { DayChipSelector } from "@/components/DayChipSelector";
import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";
import { AnniversaryEvent, eventService, EventType, RepeatRule } from "@/services/eventService";
import { LedgerTransaction, LedgerTransactionType, ledgerService } from "@/services/ledgerService";
import { MealDetail, MealRecord, MealSummary, mealService } from "@/services/mealService";
import { focusService } from "@/services/focusService";
import { Habit, HabitCheckin, habitService, serverCheckinToLocal, serverHabitToLocal } from "@/services/habitService";
import { colors } from "@/theme/colors";
import { formatMealRecognitionText } from "@/utils/mealRecognition";
import { currentMonthInShanghai, formatDateCn, formatDateTimeCn, todayInShanghai } from "@/utils/time";

type RecordsTab = "diet" | "ledger" | "events" | "calendar";

type CalendarDayState = {
  date: string;
  dayNumber: number;
  inMonth: boolean;
  isToday: boolean;
  allHabitsDone: boolean;
  hasFocus: boolean;
  hasMeal: boolean;
  hasLedger: boolean;
  hasEvent: boolean;
};

type CalendarDayDetail = {
  focus: { totalSeconds: number; sessionCount: number };
  habits: { all: Habit[]; checkins: HabitCheckin[] };
  meals: MealRecord[];
  transactions: LedgerTransaction[];
  events: AnniversaryEvent[];
};

const CATEGORIES = ["餐饮", "交通", "购物", "住房", "娱乐", "医疗", "工资", "其他"];
const EVENT_TYPES: { value: EventType; label: string }[] = [
  { value: "anniversary", label: "纪念日" },
  { value: "birthday", label: "生日" },
  { value: "important_day", label: "重要日" },
  { value: "todo_reminder", label: "提醒" },
];
const REPEAT_RULES: { value: RepeatRule; label: string }[] = [
  { value: "none", label: "不重复" },
  { value: "yearly", label: "每年" },
  { value: "monthly", label: "每月" },
  { value: "weekly", label: "每周" },
];

function currentMonth() {
  return currentMonthInShanghai();
}

function today() {
  return todayInShanghai();
}

function formatMoney(value: number) {
  return `¥${value.toFixed(2)}`;
}

function formatMealType(value: string) {
  return (
    {
      breakfast: "早餐",
      lunch: "午餐",
      dinner: "晚餐",
      snack: "加餐",
    }[value] ?? value
  );
}

export default function RecordsScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const [activeTab, setActiveTab] = useState<RecordsTab>("calendar");
  const [month] = useState(currentMonth());

  const [dietSummary, setDietSummary] = useState<MealSummary | null>(null);
  const [dietLoading, setDietLoading] = useState(false);
  const [selectedMeal, setSelectedMeal] = useState<MealDetail | null>(null);
  const [mealLoading, setMealLoading] = useState(false);
  const [mealActionLoading, setMealActionLoading] = useState(false);
  const [mealImageLoading, setMealImageLoading] = useState(false);
  const [imagePreviewVisible, setImagePreviewVisible] = useState(false);

  const [type, setType] = useState<LedgerTransactionType>("expense");
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("餐饮");
  const [account, setAccount] = useState("微信");
  const [occurredDate, setOccurredDate] = useState(today());
  const [note, setNote] = useState("");
  const [mediaAssetId, setMediaAssetId] = useState("");
  const [budgetAmount, setBudgetAmount] = useState("");
  const [transactions, setTransactions] = useState<LedgerTransaction[]>([]);
  const [summary, setSummary] = useState({
    income: 0,
    expense: 0,
    balance: 0,
    budget: 0,
    categoryExpenses: [] as { category: string; amount: number }[],
  });

  const [eventType, setEventType] = useState<EventType>("anniversary");
  const [eventTitle, setEventTitle] = useState("");
  const [eventDate, setEventDate] = useState(today());
  const [repeatRule, setRepeatRule] = useState<RepeatRule>("yearly");
  const [remindDays, setRemindDays] = useState<number[]>([7, 1]);
  const [eventNote, setEventNote] = useState("");
  const [upcomingEvents, setUpcomingEvents] = useState<AnniversaryEvent[]>([]);
  const [calendarMonth, setCalendarMonth] = useState(currentMonth());
  const [selectedDate, setSelectedDate] = useState(today());
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [calendarDays, setCalendarDays] = useState<CalendarDayState[]>([]);
  const [calendarDetail, setCalendarDetail] = useState<CalendarDayDetail | null>(null);
  const [calendarDetailLoading, setCalendarDetailLoading] = useState(false);
  const [calendarSectionsExpanded, setCalendarSectionsExpanded] = useState({
    focus: true,
    habits: true,
    meals: true,
    transactions: true,
  });

  const budgetProgress = useMemo(() => {
    if (summary.budget <= 0) return 0;
    return Math.min(summary.expense / summary.budget, 1);
  }, [summary.budget, summary.expense]);

  const loadDiet = useCallback(async () => {
    setDietLoading(true);
    try {
      const res = await mealService.getSummary();
      if (res.success && res.data) {
        setDietSummary(res.data);
      }
    } catch (error) {
      console.warn("加载饮食数据失败", error);
    } finally {
      setDietLoading(false);
    }
  }, []);

  const loadMealDetail = useCallback(async (mealId: string) => {
    setMealLoading(true);
    try {
      const res = await mealService.getMeal(mealId);
      if (res.success && res.data) {
        setSelectedMeal(res.data);
      } else {
        Alert.alert("加载失败", res.error?.message ?? "请稍后重试。");
      }
    } finally {
      setMealLoading(false);
    }
  }, []);

  useEffect(() => {
    if (pathname === "/records" || pathname === "/(tabs)/records") {
      void loadDiet();
    }
  }, [pathname, loadDiet]);

  useEffect(() => {
    void loadLedger();
    void loadEvents();
  }, []);

  useEffect(() => {
    if (activeTab === "calendar") {
      void loadCalendarMonth(calendarMonth);
    }
  }, [activeTab, calendarMonth]);

  useEffect(() => {
    if (activeTab === "calendar") {
      void loadCalendarDetail(selectedDate);
    }
  }, [activeTab, selectedDate]);

  useEffect(() => {
    setCalendarSectionsExpanded({
      focus: true,
      habits: true,
      meals: true,
      transactions: true,
    });
  }, [selectedDate]);

  async function loadLedger() {
    try {
      const [nextSummary, nextTransactions] = await Promise.all([
        ledgerService.getSummary(month),
        ledgerService.getTransactions(month),
      ]);
      setSummary(nextSummary);
      setTransactions(nextTransactions);
      setBudgetAmount(nextSummary.budget > 0 ? String(nextSummary.budget) : "");
    } catch (error) {
      console.warn("加载记账数据失败", error);
    }
  }

  async function submitTransaction() {
    const parsedAmount = Number(amount);
    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      Alert.alert("提示", "请输入大于 0 的金额。");
      return;
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(occurredDate)) {
      Alert.alert("提示", "日期格式需为 YYYY-MM-DD。");
      return;
    }

    try {
      await ledgerService.createTransaction({
        type,
        amount: parsedAmount,
        category,
        account,
        occurredAt: `${occurredDate}T12:00:00.000Z`,
        note: note.trim() || null,
        mediaAssetId: mediaAssetId.trim() || null,
      });
      setAmount("");
      setNote("");
      setMediaAssetId("");
      await loadLedger();
      Alert.alert("已记录", "这笔流水已保存到本地。");
    } catch (error) {
      Alert.alert("保存失败", error instanceof Error ? error.message : "请稍后重试。");
    }
  }

  async function saveBudget() {
    const parsedBudget = Number(budgetAmount);
    if (!Number.isFinite(parsedBudget) || parsedBudget <= 0) {
      Alert.alert("提示", "请输入大于 0 的预算金额。");
      return;
    }

    try {
      await ledgerService.saveBudget(month, {
        amount: parsedBudget,
        category: null,
      });
      await loadLedger();
      Alert.alert("已保存", "本月预算已更新。");
    } catch (error) {
      Alert.alert("保存失败", error instanceof Error ? error.message : "请稍后重试。");
    }
  }

  async function deleteTransaction(id: string) {
    try {
      await ledgerService.deleteTransaction(id);
      await loadLedger();
    } catch (error) {
      Alert.alert("删除失败", error instanceof Error ? error.message : "请稍后重试。");
    }
  }

  async function loadEvents() {
    try {
      const events = await eventService.getUpcoming(366);
      setUpcomingEvents(events);
    } catch (error) {
      console.warn("加载纪念日失败", error);
    }
  }

  async function fetchHabitsForDate(date: string) {
    try {
      const response = await habitService.getCalendarDataFromServer(date, date);
      if (!response.success || !response.data) {
        throw new Error(response.error?.message || "获取习惯失败");
      }
      const habits = response.data.habits.map(serverHabitToLocal);
      const checkins = response.data.checkins.map(serverCheckinToLocal);
      return { habits, checkins };
    } catch {
      const [habits, checkins] = await Promise.all([
        habitService.getHabits(),
        habitService.getCheckinsForDate(date),
      ]);
      return { habits, checkins };
    }
  }

  async function fetchHabitsForRange(from: string, to: string) {
    try {
      const response = await habitService.getCalendarDataFromServer(from, to);
      if (!response.success || !response.data) {
        throw new Error(response.error?.message || "获取习惯失败");
      }
      const habits = response.data.habits.map(serverHabitToLocal);
      const checkins = response.data.checkins.map(serverCheckinToLocal);
      return { habits, checkins };
    } catch {
      const [habits, checkins] = await Promise.all([
        habitService.getHabits(),
        habitService.getCheckinsForRange(from, to),
      ]);
      return { habits, checkins };
    }
  }

  async function fetchFocusForDate(date: string) {
    try {
      return await focusService.getStatsForDateFromServer(date);
    } catch {
      return await focusService.getStatsForDate(date);
    }
  }

  async function loadCalendarMonth(monthValue: string) {
    setCalendarLoading(true);
    try {
      const year = Number(monthValue.slice(0, 4));
      const monthIndex = Number(monthValue.slice(5, 7)) - 1;
      const firstDay = new Date(year, monthIndex, 1);
      const startWeekday = (firstDay.getDay() + 6) % 7;
      const gridStart = new Date(year, monthIndex, 1 - startWeekday);
      const gridEnd = new Date(gridStart);
      gridEnd.setDate(gridStart.getDate() + 41);
      const from = formatDateCn(gridStart);
      const to = formatDateCn(gridEnd);

      const [monthFocusSessions, monthHabitData, monthTransactions, monthEvents, monthMeals] = await Promise.all([
        focusService.getMonthSessionsFromServer(monthValue).catch(async () => {
          const all = await focusService.getSessions();
          return all
            .filter((session) => session.status === "completed")
            .filter((session) => String(session.started_at).slice(0, 7) === monthValue)
            .map((session) => ({
              actualSeconds: session.actual_seconds,
              startedAt: session.started_at,
              status: session.status,
            }));
        }),
        fetchHabitsForRange(from, to),
        ledgerService.getTransactions(monthValue),
        eventService.getEvents(from, to),
        mealService.listMealsByRange(from, to).then((res) => (res.success && res.data ? res.data : [])),
      ]);

      const focusCountByDate = new Map<string, number>();
      monthFocusSessions.forEach((session) => {
        const date = String(session.startedAt).slice(0, 10);
        focusCountByDate.set(date, (focusCountByDate.get(date) || 0) + 1);
      });

      const transactionCountByDate = new Map<string, number>();
      monthTransactions.forEach((transaction) => {
        const date = String(transaction.occurred_at).slice(0, 10);
        transactionCountByDate.set(date, (transactionCountByDate.get(date) || 0) + 1);
      });

      const eventCountByDate = new Map<string, number>();
      monthEvents.forEach((event) => {
        const date = event.nextOccurrenceDate;
        eventCountByDate.set(date, (eventCountByDate.get(date) || 0) + 1);
      });

      const mealCountByDate = new Map<string, number>();
      monthMeals.forEach((meal) => {
        const date = String(meal.occurredAt).slice(0, 10);
        mealCountByDate.set(date, (mealCountByDate.get(date) || 0) + 1);
      });

      const activeHabits = monthHabitData.habits.filter((habit) => !habit.is_archived);
      const completedHabitIdsByDate = new Map<string, Set<string>>();
      monthHabitData.checkins.forEach((checkin) => {
        if (checkin.count <= 0) return;
        const existing = completedHabitIdsByDate.get(checkin.checkin_date) ?? new Set<string>();
        existing.add(checkin.habit_id);
        completedHabitIdsByDate.set(checkin.checkin_date, existing);
      });

      const days = Array.from({ length: 42 }, (_, index) => {
          const current = new Date(gridStart);
          current.setDate(gridStart.getDate() + index);
          const date = formatDateCn(current);
          const inMonth = current.getMonth() === monthIndex;

          const completedHabitIds = completedHabitIdsByDate.get(date) ?? new Set<string>();
          const allHabitsDone = activeHabits.length > 0 && activeHabits.every((habit) => completedHabitIds.has(habit.id));

          return {
            date,
            dayNumber: current.getDate(),
            inMonth,
            isToday: date === today(),
            allHabitsDone,
            hasFocus: (focusCountByDate.get(date) || 0) > 0,
            hasMeal: (mealCountByDate.get(date) || 0) > 0,
            hasLedger: (transactionCountByDate.get(date) || 0) > 0,
            hasEvent: (eventCountByDate.get(date) || 0) > 0,
          } satisfies CalendarDayState;
        });

      setCalendarDays(days);
    } catch (error) {
      console.warn("加载日历失败", error);
    } finally {
      setCalendarLoading(false);
    }
  }

  async function loadCalendarDetail(date: string) {
    setCalendarDetailLoading(true);
    try {
      const [focus, habitData, mealsRes, transactions, events] = await Promise.all([
        fetchFocusForDate(date),
        fetchHabitsForDate(date),
        mealService.listMealsByDate(date),
        ledgerService.getTransactionsForDate(date),
        eventService.getEvents(date, date),
      ]);

      setCalendarDetail({
        focus,
        habits: {
          all: habitData.habits.filter((habit) => !habit.is_archived),
          checkins: habitData.checkins,
        },
        meals: mealsRes.success && mealsRes.data ? mealsRes.data : [],
        transactions,
        events,
      });
    } catch (error) {
      console.warn("加载日期详情失败", error);
    } finally {
      setCalendarDetailLoading(false);
    }
  }

  function shiftCalendarMonth(offset: number) {
    const year = Number(calendarMonth.slice(0, 4));
    const monthIndex = Number(calendarMonth.slice(5, 7)) - 1;
    const next = new Date(year, monthIndex + offset, 1);
    setCalendarMonth(`${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, "0")}`);
  }

  async function submitEvent() {
    if (!eventTitle.trim()) {
      Alert.alert("提示", "请输入事件标题。");
      return;
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(eventDate)) {
      Alert.alert("提示", "日期格式需为 YYYY-MM-DD。");
      return;
    }

    try {
      await eventService.createEvent({
        type: eventType,
        title: eventTitle,
        eventDate,
        repeatRule,
        remindDaysBefore: remindDays,
        note: eventNote.trim() || null,
      });
      setEventTitle("");
      setEventNote("");
      await loadEvents();
      Alert.alert("已保存", "这个重要日已保存到本地。");
    } catch (error) {
      Alert.alert("保存失败", error instanceof Error ? error.message : "请稍后重试。");
    }
  }

  async function deleteEvent(id: string) {
    try {
      await eventService.deleteEvent(id);
      await loadEvents();
    } catch (error) {
      Alert.alert("删除失败", error instanceof Error ? error.message : "请稍后重试。");
    }
  }

  async function deleteMeal(id: string) {
    setMealActionLoading(true);
    try {
      const res = await mealService.deleteMeal(id);
      if (!res.success) {
        Alert.alert("删除失败", res.error?.message ?? "请稍后重试。");
        return;
      }
      setSelectedMeal(null);
      await loadDiet();
      Alert.alert("已删除", "这条饮食记录已删除。");
    } finally {
      setMealActionLoading(false);
    }
  }

  async function rerunRecognition(id: string) {
    setMealActionLoading(true);
    try {
      const res = await mealService.rerunRecognition(id);
      if (!res.success || !res.data) {
        Alert.alert("重新计算失败", res.error?.message ?? "请稍后重试。");
        return;
      }
      setSelectedMeal(res.data);
      await loadDiet();
      Alert.alert("已更新", "热量识别结果已重新计算。");
    } finally {
      setMealActionLoading(false);
    }
  }

  async function refreshMealImageUrl(id: string) {
    setMealImageLoading(true);
    try {
      const res = await mealService.getMealImageUrl(id);
      if (!res.success || !res.data) {
        return;
      }
      setSelectedMeal((current) => current && current.id === id ? { ...current, imageUrl: res.data } : current);
    } finally {
      setMealImageLoading(false);
    }
  }

  return (
    <Screen title="记录">
      <View style={styles.tabRow}>
        <TabButton label="日期" active={activeTab === "calendar"} onPress={() => setActiveTab("calendar")} />
        <TabButton label="饮食" active={activeTab === "diet"} onPress={() => setActiveTab("diet")} />
        <TabButton label="记账" active={activeTab === "ledger"} onPress={() => setActiveTab("ledger")} />
        <TabButton label="纪念日" active={activeTab === "events"} onPress={() => setActiveTab("events")} />
      </View>

      {activeTab === "diet" && (
        <View style={styles.section}>
          <View style={styles.metricGrid}>
            <MetricCard
              label="今日热量"
              value={dietLoading ? "加载中..." : `${dietSummary?.todayTotalCalories ?? 0} 千卡`}
              accent="green"
            />
            <MetricCard
              label="今日记录"
              value={dietLoading ? "..." : `${dietSummary?.todayMealCount ?? 0} 条`}
              accent="teal"
            />
          </View>

          <TouchableOpacity style={styles.uploadCard} onPress={() => router.push("/meal-upload")}>
            <View style={styles.iconContainer}>
              <MaterialCommunityIcons name="camera-plus" size={32} color={colors.accent} />
            </View>
            <View style={styles.uploadContent}>
              <Text style={styles.uploadTitle}>饮食拍照</Text>
              <Text style={styles.uploadSubtitle}>上传照片后识别食物热量</Text>
            </View>
            <MaterialCommunityIcons name="chevron-right" size={24} color={colors.muted} />
          </TouchableOpacity>

          <View style={styles.panel}>
            <View style={styles.panelHeader}>
              <Text style={styles.panelTitle}>最近饮食记录</Text>
              {dietLoading ? <ActivityIndicator size="small" color={colors.accent} /> : null}
            </View>
            {!dietSummary || dietSummary.recentMeals.length === 0 ? (
              <Text style={styles.emptyText}>暂无饮食记录，先试试拍照识别吧。</Text>
            ) : (
              dietSummary.recentMeals.map((meal) => (
                <Pressable key={meal.id} style={styles.transactionRow} onPress={() => loadMealDetail(meal.id)}>
                  <View style={styles.transactionMain}>
                    <Text style={styles.transactionTitle}>{formatMealType(meal.mealType)}</Text>
                    <Text style={styles.transactionMeta}>
                      {formatDateTimeCn(meal.occurredAt)} · {meal.aiGenerated ? "AI 识别" : "手动记录"}
                    </Text>
                  </View>
                  <Text style={[styles.transactionAmount, styles.eventCountdown]}>
                    {meal.totalCalories ?? 0} 千卡
                  </Text>
                  <MaterialCommunityIcons name="chevron-right" size={18} color={colors.muted} />
                </Pressable>
              ))
            )}
          </View>

          <View style={styles.panel}>
            <View style={styles.panelHeader}>
              <Text style={styles.panelTitle}>记录详情</Text>
              {mealLoading ? <ActivityIndicator size="small" color={colors.accent} /> : null}
            </View>
            {!selectedMeal ? (
              <Text style={styles.emptyText}>点击一条饮食记录即可查看图片、识别结果并重新计算热量。</Text>
            ) : (
              <>
                <Text style={styles.detailTitle}>{formatMealType(selectedMeal.mealType)}</Text>
                <Text style={styles.detailMeta}>
                  {formatDateTimeCn(selectedMeal.occurredAt)} · {selectedMeal.aiGenerated ? "AI 识别生成" : "手动记录"}
                </Text>
                <Text style={styles.detailCalories}>
                  总热量 {selectedMeal.totalCalories ?? 0} 千卡
                </Text>
                <View style={styles.inlineHeader}>
                  <Text style={styles.detailLabel}>图片</Text>
                  {selectedMeal.imageUrl ? (
                    <TouchableOpacity onPress={() => refreshMealImageUrl(selectedMeal.id)} disabled={mealImageLoading}>
                      <Text style={styles.refreshLink}>{mealImageLoading ? "刷新中..." : "刷新图片"}</Text>
                    </TouchableOpacity>
                  ) : null}
                </View>
                {selectedMeal.imageUrl ? (
                  <Pressable onPress={() => setImagePreviewVisible(true)}>
                    <Image
                      source={{ uri: selectedMeal.imageUrl }}
                      style={styles.mealImage}
                      resizeMode="contain"
                      onError={() => refreshMealImageUrl(selectedMeal.id)}
                    />
                  </Pressable>
                ) : (
                  <Text style={styles.detailBody}>当前记录没有可查看的图片。</Text>
                )}
                <Text style={styles.detailLabel}>识别结果</Text>
                <Text style={styles.detailBody}>
                  {formatMealRecognitionText(selectedMeal.note) || "暂无识别结果说明。"}
                </Text>
                <Text style={styles.disclaimer}>
                  AI 识别结果仅供参考，不构成医学或营养建议。
                </Text>
                <View style={styles.actionRow}>
                  <TouchableOpacity
                    style={[styles.secondaryActionButton, mealActionLoading && styles.disabledButton]}
                    disabled={mealActionLoading}
                    onPress={() => rerunRecognition(selectedMeal.id)}
                  >
                    <Text style={styles.secondaryActionText}>重新计算热量</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.dangerButton, mealActionLoading && styles.disabledButton]}
                    disabled={mealActionLoading}
                    onPress={() => deleteMeal(selectedMeal.id)}
                  >
                    <Text style={styles.dangerButtonText}>删除记录</Text>
                  </TouchableOpacity>
                </View>
              </>
            )}
          </View>
        </View>
      )}

      {activeTab === "ledger" && (
        <View style={styles.section}>
          <View style={styles.metricGrid}>
            <MetricCard label="本月收入" value={formatMoney(summary.income)} accent="teal" />
            <MetricCard label="本月支出" value={formatMoney(summary.expense)} accent="amber" />
            <MetricCard label="本月结余" value={formatMoney(summary.balance)} accent="blue" />
            <MetricCard label="本月预算" value={formatMoney(summary.budget)} accent="slate" />
          </View>

          <View style={styles.panel}>
            <View style={styles.panelHeader}>
              <Text style={styles.panelTitle}>{month} 预算</Text>
              <Text style={styles.panelMeta}>{Math.round(budgetProgress * 100)}%</Text>
            </View>
            <View style={styles.progressTrack}>
              <View style={[styles.progressFill, { width: `${budgetProgress * 100}%` }]} />
            </View>
            <View style={styles.row}>
              <TextInput
                style={[styles.input, styles.rowInput]}
                value={budgetAmount}
                onChangeText={setBudgetAmount}
                keyboardType="decimal-pad"
                placeholder="预算金额"
                placeholderTextColor={colors.muted}
              />
              <TouchableOpacity style={styles.darkButton} onPress={saveBudget}>
                <Text style={styles.darkButtonText}>保存</Text>
              </TouchableOpacity>
            </View>
          </View>

          <View style={styles.panel}>
            <Text style={styles.panelTitle}>新增流水</Text>
            <View style={styles.segmented}>
              {(["expense", "income", "transfer"] as LedgerTransactionType[]).map((item) => (
                <TouchableOpacity
                  key={item}
                  style={[styles.segment, type === item && styles.segmentActive]}
                  onPress={() => setType(item)}
                >
                  <Text style={[styles.segmentText, type === item && styles.segmentTextActive]}>
                    {item === "expense" ? "支出" : item === "income" ? "收入" : "转账"}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <TextInput
              style={styles.input}
              value={amount}
              onChangeText={setAmount}
              keyboardType="decimal-pad"
              placeholder="金额"
              placeholderTextColor={colors.muted}
            />
            <View style={styles.categoryGrid}>
              {CATEGORIES.map((item) => (
                <TouchableOpacity
                  key={item}
                  style={[styles.categoryButton, category === item && styles.categoryButtonActive]}
                  onPress={() => setCategory(item)}
                >
                  <Text style={[styles.categoryText, category === item && styles.categoryTextActive]}>
                    {item}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
            <View style={styles.twoColumns}>
              <TextInput
                style={[styles.input, styles.halfInput]}
                value={account}
                onChangeText={setAccount}
                placeholder="账户"
                placeholderTextColor={colors.muted}
              />
              <View style={styles.halfInput}>
                <DateInput value={occurredDate} onChange={setOccurredDate} />
              </View>
            </View>
            <TextInput
              style={styles.input}
              value={note}
              onChangeText={setNote}
              placeholder="备注"
              placeholderTextColor={colors.muted}
            />
            <TextInput
              style={styles.input}
              value={mediaAssetId}
              onChangeText={setMediaAssetId}
              placeholder="凭证图片 ID（可选）"
              placeholderTextColor={colors.muted}
            />
            <TouchableOpacity style={styles.primaryButton} onPress={submitTransaction}>
              <MaterialCommunityIcons name="plus" size={20} color={colors.surface} />
              <Text style={styles.primaryButtonText}>保存流水</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.panel}>
            <Text style={styles.panelTitle}>最近流水</Text>
            {transactions.length === 0 ? (
              <Text style={styles.emptyText}>暂无记账记录</Text>
            ) : (
              transactions.slice(0, 8).map((transaction) => (
                <View key={transaction.id} style={styles.transactionRow}>
                  <View style={styles.transactionMain}>
                    <Text style={styles.transactionTitle}>{transaction.category || "未分类"}</Text>
                    <Text style={styles.transactionMeta}>
                      {formatDateCn(transaction.occurred_at)} · {transaction.account || "默认账户"}
                    </Text>
                  </View>
                  <Text
                    style={[
                      styles.transactionAmount,
                      transaction.type === "income" && styles.incomeAmount,
                    ]}
                  >
                    {transaction.type === "income" ? "+" : "-"}
                    {formatMoney(transaction.amount)}
                  </Text>
                  <TouchableOpacity style={styles.iconButton} onPress={() => deleteTransaction(transaction.id)}>
                    <MaterialCommunityIcons name="trash-can-outline" size={18} color={colors.muted} />
                  </TouchableOpacity>
                </View>
              ))
            )}
          </View>
        </View>
      )}

      {activeTab === "events" && (
        <View style={styles.section}>
          <MetricCard label="未来一年事件" value={`${upcomingEvents.length} 条`} accent="blue" />

          <View style={styles.panel}>
            <Text style={styles.panelTitle}>新增重要日</Text>
            <View style={styles.categoryGrid}>
              {EVENT_TYPES.map((item) => (
                <TouchableOpacity
                  key={item.value}
                  style={[styles.categoryButton, eventType === item.value && styles.categoryButtonActive]}
                  onPress={() => setEventType(item.value)}
                >
                  <Text style={[styles.categoryText, eventType === item.value && styles.categoryTextActive]}>
                    {item.label}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
            <TextInput
              style={styles.input}
              value={eventTitle}
              onChangeText={setEventTitle}
              placeholder="标题"
              placeholderTextColor={colors.muted}
            />
            <DateInput value={eventDate} onChange={setEventDate} />

            <DayChipSelector selected={remindDays} onChange={setRemindDays} />

            <View style={styles.categoryGrid}>
              {REPEAT_RULES.map((item) => (
                <TouchableOpacity
                  key={item.value}
                  style={[styles.categoryButton, repeatRule === item.value && styles.categoryButtonActive]}
                  onPress={() => setRepeatRule(item.value)}
                >
                  <Text style={[styles.categoryText, repeatRule === item.value && styles.categoryTextActive]}>
                    {item.label}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
            <TextInput
              style={styles.input}
              value={eventNote}
              onChangeText={setEventNote}
              placeholder="备注"
              placeholderTextColor={colors.muted}
            />
            <TouchableOpacity style={styles.primaryButton} onPress={submitEvent}>
              <MaterialCommunityIcons name="calendar-plus" size={20} color={colors.surface} />
              <Text style={styles.primaryButtonText}>保存重要日</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.panel}>
            <Text style={styles.panelTitle}>即将到来</Text>
            {upcomingEvents.length === 0 ? (
              <Text style={styles.emptyText}>暂无纪念日或提醒</Text>
            ) : (
              upcomingEvents.slice(0, 8).map((event) => (
                <View key={event.id} style={styles.transactionRow}>
                  <View style={styles.transactionMain}>
                    <Text style={styles.transactionTitle}>{event.title}</Text>
                    <Text style={styles.transactionMeta}>
                      {formatDateCn(event.nextOccurrenceDate)} · {event.repeat_rule === "none" ? "不重复" : "重复"}
                    </Text>
                  </View>
                  <Text style={[styles.transactionAmount, styles.eventCountdown]}>
                    {event.daysUntil === 0 ? "今天" : `${event.daysUntil} 天`}
                  </Text>
                  <TouchableOpacity style={styles.iconButton} onPress={() => deleteEvent(event.id)}>
                    <MaterialCommunityIcons name="trash-can-outline" size={18} color={colors.muted} />
                  </TouchableOpacity>
                </View>
              ))
            )}
          </View>
        </View>
      )}

      {activeTab === "calendar" && (
        <View style={styles.section}>
          <View style={styles.panel}>
            <View style={styles.panelHeader}>
              <TouchableOpacity style={styles.iconButton} onPress={() => shiftCalendarMonth(-1)}>
                <MaterialCommunityIcons name="chevron-left" size={20} color={colors.text} />
              </TouchableOpacity>
              <Text style={styles.panelTitle}>{calendarMonth}</Text>
              <TouchableOpacity style={styles.iconButton} onPress={() => shiftCalendarMonth(1)}>
                <MaterialCommunityIcons name="chevron-right" size={20} color={colors.text} />
              </TouchableOpacity>
            </View>

            <View style={styles.calendarWeekHeader}>
              {["一", "二", "三", "四", "五", "六", "日"].map((label) => (
                <Text key={label} style={styles.calendarWeekLabel}>{label}</Text>
              ))}
            </View>

            {calendarLoading ? (
              <ActivityIndicator size="small" color={colors.accent} />
            ) : (
              <View style={styles.calendarGrid}>
                {calendarDays.map((day) => (
                  <TouchableOpacity
                    key={day.date}
                    style={[
                      styles.calendarCell,
                      !day.inMonth && styles.calendarCellMuted,
                      day.isToday && styles.calendarCellToday,
                      selectedDate === day.date && styles.calendarCellSelected,
                    ]}
                    onPress={() => setSelectedDate(day.date)}
                  >
                    <View style={styles.calendarCellTop}>
                      <Text
                        style={[
                          styles.calendarDayText,
                          !day.inMonth && styles.calendarDayTextMuted,
                          selectedDate === day.date && styles.calendarDayTextSelected,
                        ]}
                      >
                        {day.dayNumber}
                      </Text>
                      {day.allHabitsDone ? (
                        <MaterialCommunityIcons name="check-circle" size={14} color={colors.accent} />
                      ) : null}
                    </View>
                    <View style={styles.calendarDotRow}>
                      {day.hasFocus ? <View style={[styles.calendarDot, styles.calendarDotFocus]} /> : null}
                      {day.hasMeal ? <View style={[styles.calendarDot, styles.calendarDotMeal]} /> : null}
                      {day.hasLedger ? <View style={[styles.calendarDot, styles.calendarDotLedger]} /> : null}
                      {day.hasEvent ? <View style={[styles.calendarDot, styles.calendarDotEvent]} /> : null}
                    </View>
                  </TouchableOpacity>
                ))}
              </View>
            )}
          </View>

          <View style={styles.panel}>
            <View style={styles.panelHeader}>
              <Text style={styles.panelTitle}>{selectedDate} 记录</Text>
              {calendarDetailLoading ? <ActivityIndicator size="small" color={colors.accent} /> : null}
            </View>

            {!calendarDetail ? (
              <Text style={styles.emptyText}>请选择日期查看详情。</Text>
            ) : (
              <>
                <Text style={styles.detailLabel}>纪念日 / 提醒</Text>
                {calendarDetail.events.length === 0 ? (
                  <Text style={styles.emptyText}>当天没有纪念日或提醒。</Text>
                ) : (
                  calendarDetail.events.map((event) => (
                    <View key={event.id} style={styles.transactionRow}>
                      <View style={styles.transactionMain}>
                        <Text style={styles.transactionTitle}>{event.title}</Text>
                        <Text style={styles.transactionMeta}>{event.type}</Text>
                      </View>
                      <Text style={[styles.transactionAmount, styles.eventCountdown]}>当天</Text>
                    </View>
                  ))
                )}

                <DetailSection
                  label="专注"
                  expanded={calendarSectionsExpanded.focus}
                  onToggle={() =>
                    setCalendarSectionsExpanded((current) => ({ ...current, focus: !current.focus }))
                  }
                >
                  <View style={styles.transactionRow}>
                    <View style={styles.transactionMain}>
                      <Text style={styles.transactionTitle}>专注记录</Text>
                      <Text style={styles.transactionMeta}>已完成 {calendarDetail.focus.sessionCount} 次</Text>
                    </View>
                    <Text style={[styles.transactionAmount, styles.eventCountdown]}>
                      {Math.floor(calendarDetail.focus.totalSeconds / 60)} 分钟
                    </Text>
                  </View>
                </DetailSection>

                <DetailSection
                  label="习惯"
                  expanded={calendarSectionsExpanded.habits}
                  onToggle={() =>
                    setCalendarSectionsExpanded((current) => ({ ...current, habits: !current.habits }))
                  }
                >
                  {calendarDetail.habits.all.length === 0 ? (
                    <Text style={styles.emptyText}>当天没有习惯项目。</Text>
                  ) : (
                    calendarDetail.habits.all.map((habit) => {
                      const done = calendarDetail.habits.checkins.some((checkin) => checkin.habit_id === habit.id && checkin.count > 0);
                      return (
                        <View key={habit.id} style={styles.transactionRow}>
                          <View style={styles.transactionMain}>
                            <Text style={styles.transactionTitle}>{habit.name}</Text>
                            <Text style={styles.transactionMeta}>{done ? "已打卡" : "未打卡"}</Text>
                          </View>
                          <Text style={[styles.transactionAmount, done && styles.incomeAmount]}>
                            {done ? "已完成" : "未完成"}
                          </Text>
                        </View>
                      );
                    })
                  )}
                </DetailSection>

                <DetailSection
                  label="饮食"
                  expanded={calendarSectionsExpanded.meals}
                  onToggle={() =>
                    setCalendarSectionsExpanded((current) => ({ ...current, meals: !current.meals }))
                  }
                >
                  {calendarDetail.meals.length === 0 ? (
                    <Text style={styles.emptyText}>当天没有饮食记录。</Text>
                  ) : (
                    calendarDetail.meals.map((meal) => (
                      <Pressable key={meal.id} style={styles.transactionRow} onPress={() => loadMealDetail(meal.id)}>
                        <View style={styles.transactionMain}>
                          <Text style={styles.transactionTitle}>{formatMealType(meal.mealType)}</Text>
                          <Text style={styles.transactionMeta}>{formatDateTimeCn(meal.occurredAt)}</Text>
                        </View>
                        <Text style={[styles.transactionAmount, styles.eventCountdown]}>{meal.totalCalories ?? 0} 千卡</Text>
                      </Pressable>
                    ))
                  )}
                </DetailSection>

                <DetailSection
                  label="记账"
                  expanded={calendarSectionsExpanded.transactions}
                  onToggle={() =>
                    setCalendarSectionsExpanded((current) => ({ ...current, transactions: !current.transactions }))
                  }
                >
                  {calendarDetail.transactions.length === 0 ? (
                    <Text style={styles.emptyText}>当天没有流水记录。</Text>
                  ) : (
                    calendarDetail.transactions.map((transaction) => (
                      <View key={transaction.id} style={styles.transactionRow}>
                        <View style={styles.transactionMain}>
                          <Text style={styles.transactionTitle}>{transaction.category || "未分类"}</Text>
                          <Text style={styles.transactionMeta}>{transaction.account || "默认账户"}</Text>
                        </View>
                        <Text style={[styles.transactionAmount, transaction.type === "income" && styles.incomeAmount]}>
                          {transaction.type === "income" ? "+" : "-"}{formatMoney(transaction.amount)}
                        </Text>
                      </View>
                    ))
                  )}
                </DetailSection>
              </>
            )}
          </View>
        </View>
      )}

      <Modal
        visible={imagePreviewVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setImagePreviewVisible(false)}
      >
        <Pressable style={styles.previewOverlay} onPress={() => setImagePreviewVisible(false)}>
          <View style={styles.previewHeader}>
            <TouchableOpacity style={styles.previewCloseButton} onPress={() => setImagePreviewVisible(false)}>
              <MaterialCommunityIcons name="close" size={24} color={colors.surface} />
            </TouchableOpacity>
          </View>
          {selectedMeal?.imageUrl ? (
            <Pressable style={styles.previewContent} onPress={(event) => event.stopPropagation()}>
              <Image
                source={{ uri: selectedMeal.imageUrl }}
                style={styles.previewImage}
                resizeMode="contain"
              />
            </Pressable>
          ) : null}
        </Pressable>
      </Modal>
    </Screen>
  );
}

function TabButton({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.tabButton, active && styles.tabButtonActive]}>
      <Text style={[styles.tabButtonText, active && styles.tabButtonTextActive]}>{label}</Text>
    </Pressable>
  );
}

function DetailSection({
  label,
  expanded,
  onToggle,
  children,
}: {
  label: string;
  expanded: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <View style={styles.detailSection}>
      <TouchableOpacity style={styles.detailSectionHeader} onPress={onToggle} activeOpacity={0.85}>
        <Text style={styles.detailLabel}>{label}</Text>
        <View style={styles.detailSectionAction}>
          <Text style={styles.detailSectionActionText}>{expanded ? "收起" : "展开查看"}</Text>
          <MaterialCommunityIcons
            name={expanded ? "chevron-up" : "chevron-down"}
            size={18}
            color={colors.muted}
          />
        </View>
      </TouchableOpacity>
      {expanded ? children : null}
    </View>
  );
}

const styles = StyleSheet.create({
  actionRow: {
    flexDirection: "row",
    gap: 10,
  },
  categoryButton: {
    alignItems: "center",
    borderColor: colors.border,
    borderRadius: 10,
    borderWidth: 1,
    minWidth: 74,
    paddingHorizontal: 12,
    paddingVertical: 9,
  },
  categoryButtonActive: {
    backgroundColor: colors.accent,
    borderColor: colors.accent,
  },
  categoryGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  categoryText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "600",
  },
  categoryTextActive: {
    color: colors.surface,
  },
  calendarCell: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    minHeight: 68,
    padding: 8,
    width: "13.4%",
  },
  calendarCellMuted: {
    opacity: 0.45,
  },
  calendarCellSelected: {
    borderColor: colors.accent,
    backgroundColor: `${colors.accent}10`,
  },
  calendarCellToday: {
    borderColor: colors.text,
  },
  calendarCellTop: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  calendarDayText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "700",
  },
  calendarDayTextMuted: {
    color: colors.muted,
  },
  calendarDayTextSelected: {
    color: colors.accent,
  },
  calendarDot: {
    borderRadius: 999,
    height: 6,
    width: 6,
  },
  calendarDotEvent: {
    backgroundColor: "#8B5CF6",
  },
  calendarDotFocus: {
    backgroundColor: "#3B82F6",
  },
  calendarDotLedger: {
    backgroundColor: "#F59E0B",
  },
  calendarDotMeal: {
    backgroundColor: "#10B981",
  },
  calendarDotRow: {
    columnGap: 4,
    flexDirection: "row",
    flexWrap: "wrap",
    marginTop: 8,
  },
  calendarGrid: {
    columnGap: 6,
    flexDirection: "row",
    flexWrap: "wrap",
    rowGap: 6,
  },
  calendarWeekHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  calendarWeekLabel: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: "700",
    textAlign: "center",
    width: "13.4%",
  },
  dangerButton: {
    alignItems: "center",
    backgroundColor: "#FEF2F2",
    borderRadius: 10,
    flex: 1,
    justifyContent: "center",
    minHeight: 44,
    paddingHorizontal: 18,
  },
  dangerButtonText: {
    color: colors.error,
    fontSize: 15,
    fontWeight: "700",
  },
  darkButton: {
    alignItems: "center",
    backgroundColor: colors.text,
    borderRadius: 10,
    justifyContent: "center",
    minHeight: 44,
    paddingHorizontal: 18,
  },
  darkButtonText: {
    color: colors.surface,
    fontSize: 15,
    fontWeight: "700",
  },
  detailBody: {
    color: colors.text,
    fontSize: 14,
    lineHeight: 22,
  },
  detailCalories: {
    color: colors.accent,
    fontSize: 18,
    fontWeight: "800",
  },
  detailLabel: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: "700",
    marginTop: 6,
  },
  detailSection: {
    gap: 8,
  },
  detailSectionAction: {
    alignItems: "center",
    flexDirection: "row",
    gap: 2,
  },
  detailSectionActionText: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: "700",
  },
  detailSectionHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  detailMeta: {
    color: colors.muted,
    fontSize: 13,
  },
  detailTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "800",
  },
  disclaimer: {
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18,
  },
  disabledButton: {
    opacity: 0.5,
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 20,
    paddingVertical: 10,
  },
  eventCountdown: {
    color: colors.accent,
  },
  halfInput: {
    flex: 1,
  },
  iconButton: {
    alignItems: "center",
    height: 34,
    justifyContent: "center",
    width: 34,
  },
  iconContainer: {
    alignItems: "center",
    backgroundColor: `${colors.accent}10`,
    borderRadius: 10,
    height: 56,
    justifyContent: "center",
    width: 56,
  },
  incomeAmount: {
    color: colors.accent,
  },
  inlineHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  input: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 10,
    borderWidth: 1,
    color: colors.text,
    fontSize: 15,
    minHeight: 44,
    paddingHorizontal: 12,
  },
  metricGrid: {
    gap: 10,
  },
  mealImage: {
    backgroundColor: colors.background,
    borderRadius: 14,
    height: 220,
    width: "100%",
  },
  panel: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 16,
    borderWidth: 1,
    gap: 12,
    padding: 16,
  },
  panelHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  panelMeta: {
    color: colors.muted,
    fontSize: 14,
    fontWeight: "700",
  },
  panelTitle: {
    color: colors.text,
    fontSize: 17,
    fontWeight: "800",
  },
  previewCloseButton: {
    alignItems: "center",
    backgroundColor: "rgba(15,23,42,0.6)",
    borderRadius: 999,
    height: 40,
    justifyContent: "center",
    width: 40,
  },
  previewHeader: {
    alignItems: "flex-end",
    left: 0,
    paddingHorizontal: 20,
    paddingTop: 56,
    position: "absolute",
    right: 0,
    top: 0,
    zIndex: 2,
  },
  previewContent: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center",
    width: "100%",
  },
  previewImage: {
    height: "78%",
    maxHeight: "78%",
    maxWidth: "92%",
    width: "92%",
  },
  previewOverlay: {
    alignItems: "center",
    backgroundColor: "rgba(15,23,42,0.92)",
    flex: 1,
    justifyContent: "center",
  },
  primaryButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 10,
    flexDirection: "row",
    gap: 8,
    justifyContent: "center",
    minHeight: 46,
  },
  primaryButtonText: {
    color: colors.surface,
    fontSize: 16,
    fontWeight: "700",
  },
  progressFill: {
    backgroundColor: colors.accent,
    borderRadius: 999,
    height: "100%",
  },
  progressTrack: {
    backgroundColor: colors.background,
    borderRadius: 999,
    height: 8,
    overflow: "hidden",
  },
  refreshLink: {
    color: colors.accent,
    fontSize: 13,
    fontWeight: "700",
  },
  row: {
    alignItems: "center",
    flexDirection: "row",
    gap: 10,
  },
  rowInput: {
    flex: 1,
  },
  secondaryActionButton: {
    alignItems: "center",
    borderColor: colors.accent,
    borderRadius: 10,
    borderWidth: 1,
    flex: 1,
    justifyContent: "center",
    minHeight: 44,
    paddingHorizontal: 18,
  },
  secondaryActionText: {
    color: colors.accent,
    fontSize: 15,
    fontWeight: "700",
  },
  section: {
    gap: 14,
    marginBottom: 20,
    marginTop: 18,
  },
  segmented: {
    backgroundColor: colors.background,
    borderRadius: 10,
    flexDirection: "row",
    padding: 4,
  },
  segment: {
    alignItems: "center",
    borderRadius: 8,
    flex: 1,
    paddingVertical: 10,
  },
  segmentActive: {
    backgroundColor: colors.accent,
  },
  segmentText: {
    color: colors.muted,
    fontSize: 14,
    fontWeight: "700",
  },
  segmentTextActive: {
    color: colors.surface,
  },
  tabButton: {
    borderColor: colors.border,
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  tabButtonActive: {
    backgroundColor: colors.text,
    borderColor: colors.text,
  },
  tabButtonText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "700",
  },
  tabButtonTextActive: {
    color: colors.surface,
  },
  tabRow: {
    flexDirection: "row",
    gap: 10,
    marginTop: 6,
  },
  transactionAmount: {
    color: colors.error,
    fontSize: 15,
    fontWeight: "800",
    minWidth: 86,
    textAlign: "right",
  },
  transactionMain: {
    flex: 1,
  },
  transactionMeta: {
    color: colors.muted,
    fontSize: 12,
    marginTop: 2,
  },
  transactionRow: {
    alignItems: "center",
    borderTopColor: colors.border,
    borderTopWidth: 1,
    flexDirection: "row",
    gap: 8,
    minHeight: 58,
    paddingTop: 10,
  },
  transactionTitle: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "700",
  },
  twoColumns: {
    flexDirection: "row",
    gap: 10,
  },
  uploadCard: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 16,
    borderWidth: 1,
    flexDirection: "row",
    padding: 16,
  },
  uploadContent: {
    flex: 1,
    marginLeft: 16,
  },
  uploadSubtitle: {
    color: colors.muted,
    fontSize: 13,
    marginTop: 2,
  },
  uploadTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700",
  },
});
