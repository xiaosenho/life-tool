import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useRouter } from "expo-router";
import { useFocusEffect } from "@react-navigation/core";
import { MaterialCommunityIcons } from "@expo/vector-icons";

import { MetricCard } from "@/components/MetricCard";
import { Screen } from "@/components/Screen";
import {
  ledgerService,
  LedgerTransaction,
  LedgerTransactionType,
} from "@/services/ledgerService";
import {
  AnniversaryEvent,
  eventService,
  EventType,
  RepeatRule,
} from "@/services/eventService";
import { MealSummary, mealService } from "@/services/mealService";
import { colors } from "@/theme/colors";
import { DateInput } from "@/components/DateInput";
import { DayChipSelector } from "@/components/DayChipSelector";

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
  return new Date().toISOString().slice(0, 7);
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function formatMoney(value: number) {
  return `¥${value.toFixed(2)}`;
}

export default function RecordsScreen() {
  const router = useRouter();
  const [month] = useState(currentMonth());
  const [type, setType] = useState<LedgerTransactionType>("expense");
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("餐饮");
  const [account, setAccount] = useState("微信");
  const [occurredDate, setOccurredDate] = useState(today());
  const [note, setNote] = useState("");
  const [mediaAssetId, setMediaAssetId] = useState("");
  const [budgetAmount, setBudgetAmount] = useState("");
  const [transactions, setTransactions] = useState<LedgerTransaction[]>([]);
  const [eventType, setEventType] = useState<EventType>("anniversary");
  const [eventTitle, setEventTitle] = useState("");
  const [eventDate, setEventDate] = useState(today());
  const [repeatRule, setRepeatRule] = useState<RepeatRule>("yearly");
  const [remindDays, setRemindDays] = useState<number[]>([7, 1]);
  const [eventNote, setEventNote] = useState("");
  const [upcomingEvents, setUpcomingEvents] = useState<AnniversaryEvent[]>([]);
  const [dietSummary, setDietSummary] = useState<MealSummary | null>(null);
  const [dietLoading, setDietLoading] = useState(false);
  const [summary, setSummary] = useState({
    income: 0,
    expense: 0,
    balance: 0,
    budget: 0,
    categoryExpenses: [] as { category: string; amount: number }[],
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

  useFocusEffect(
    useCallback(() => {
      void loadDiet();
    }, [loadDiet])
  );

  useEffect(() => {
    loadLedger();
    loadEvents();
  }, []);

  const loadLedger = async () => {
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
  };

  const submitTransaction = async () => {
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
  };

  const saveBudget = async () => {
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
  };

  const deleteTransaction = async (id: string) => {
    try {
      await ledgerService.deleteTransaction(id);
      await loadLedger();
    } catch (error) {
      Alert.alert("删除失败", error instanceof Error ? error.message : "请稍后重试。");
    }
  };

  const loadEvents = async () => {
    try {
      const events = await eventService.getUpcoming(366);
      setUpcomingEvents(events);
    } catch (error) {
      console.warn("加载纪念日失败", error);
    }
  };

  const submitEvent = async () => {
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
  };

  const deleteEvent = async (id: string) => {
    try {
      await eventService.deleteEvent(id);
      await loadEvents();
    } catch (error) {
      Alert.alert("删除失败", error instanceof Error ? error.message : "请稍后重试。");
    }
  };

  return (
    <Screen title="记录">
      <View style={styles.section}>
        <MetricCard
          label="饮食"
          value={
            dietLoading
              ? "加载中..."
              : dietSummary
                ? `${dietSummary.todayTotalCalories ?? 0} 千卡`
                : "0 千卡"
          }
          accent="green"
        />

        <TouchableOpacity
          style={styles.uploadCard}
          onPress={() => router.push("/meal-upload")}
        >
          <View style={styles.iconContainer}>
            <MaterialCommunityIcons name="camera-plus" size={32} color={colors.accent} />
          </View>
          <View style={styles.uploadContent}>
            <Text style={styles.uploadTitle}>饮食拍照</Text>
            <Text style={styles.uploadSubtitle}>上传照片后识别食物热量</Text>
          </View>
          <MaterialCommunityIcons name="chevron-right" size={24} color={colors.muted} />
        </TouchableOpacity>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>记账</Text>
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
                <Text
                  style={[
                    styles.categoryText,
                    category === item && styles.categoryTextActive,
                  ]}
                >
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
                  <Text style={styles.transactionTitle}>
                    {transaction.category || "未分类"}
                  </Text>
                  <Text style={styles.transactionMeta}>
                    {transaction.occurred_at.slice(0, 10)} · {transaction.account || "默认账户"}
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
                <TouchableOpacity
                  style={styles.iconButton}
                  onPress={() => deleteTransaction(transaction.id)}
                >
                  <MaterialCommunityIcons name="trash-can-outline" size={18} color={colors.muted} />
                </TouchableOpacity>
              </View>
            ))
          )}
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>纪念日</Text>
        <MetricCard label="未来一年事件" value={`${upcomingEvents.length} 条`} accent="blue" />

        <View style={styles.panel}>
          <Text style={styles.panelTitle}>新增重要日</Text>
          <View style={styles.categoryGrid}>
            {EVENT_TYPES.map((item) => (
              <TouchableOpacity
                key={item.value}
                style={[
                  styles.categoryButton,
                  eventType === item.value && styles.categoryButtonActive,
                ]}
                onPress={() => setEventType(item.value)}
              >
                <Text
                  style={[
                    styles.categoryText,
                    eventType === item.value && styles.categoryTextActive,
                  ]}
                >
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
                style={[
                  styles.categoryButton,
                  repeatRule === item.value && styles.categoryButtonActive,
                ]}
                onPress={() => setRepeatRule(item.value)}
              >
                <Text
                  style={[
                    styles.categoryText,
                    repeatRule === item.value && styles.categoryTextActive,
                  ]}
                >
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
                    {event.nextOccurrenceDate} · {event.repeat_rule === "none" ? "不重复" : "重复"}
                  </Text>
                </View>
                <Text style={[styles.transactionAmount, styles.eventCountdown]}>
                  {event.daysUntil === 0 ? "今天" : `${event.daysUntil} 天`}
                </Text>
                <TouchableOpacity
                  style={styles.iconButton}
                  onPress={() => deleteEvent(event.id)}
                >
                  <MaterialCommunityIcons name="trash-can-outline" size={18} color={colors.muted} />
                </TouchableOpacity>
              </View>
            ))
          )}
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  categoryButton: {
    alignItems: "center",
    borderColor: colors.border,
    borderRadius: 8,
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
  darkButton: {
    alignItems: "center",
    backgroundColor: colors.text,
    borderRadius: 8,
    justifyContent: "center",
    minHeight: 44,
    paddingHorizontal: 18,
  },
  darkButtonText: {
    color: colors.surface,
    fontSize: 15,
    fontWeight: "700",
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
    paddingVertical: 10,
  },
  halfInput: {
    flex: 1,
  },
  eventCountdown: {
    color: colors.accent,
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
    borderRadius: 8,
    height: 56,
    justifyContent: "center",
    width: 56,
  },
  incomeAmount: {
    color: colors.accent,
  },
  input: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    color: colors.text,
    fontSize: 15,
    minHeight: 44,
    paddingHorizontal: 12,
  },
  metricGrid: {
    gap: 10,
  },
  panel: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    gap: 12,
    padding: 14,
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
  primaryButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 8,
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
  row: {
    alignItems: "center",
    flexDirection: "row",
    gap: 10,
  },
  rowInput: {
    flex: 1,
  },
  section: {
    gap: 12,
    marginBottom: 20,
  },
  sectionTitle: {
    color: colors.text,
    fontSize: 22,
    fontWeight: "800",
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
  segmented: {
    backgroundColor: colors.background,
    borderRadius: 8,
    flexDirection: "row",
    padding: 4,
  },
  segmentText: {
    color: colors.muted,
    fontSize: 14,
    fontWeight: "700",
  },
  segmentTextActive: {
    color: colors.surface,
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
    borderRadius: 8,
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
