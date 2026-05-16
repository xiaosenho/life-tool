import React, { useCallback, useMemo, useRef, useState } from "react";
import {
  Modal,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import { colors } from "@/theme/colors";

interface DateInputProps {
  value: string;
  onChange: (date: string) => void;
}

function formatDisplay(date: string) {
  if (!date) return "选择日期";
  const d = new Date(date + "T00:00:00");
  if (isNaN(d.getTime())) return date;
  const year = d.getFullYear();
  const month = d.getMonth() + 1;
  const day = d.getDate();
  const weekDay = ["日", "一", "二", "三", "四", "五", "六"][d.getDay()];
  return `${year}年${month}月${day}日 周${weekDay}`;
}

function daysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate();
}

export function DateInput({ value, onChange }: DateInputProps) {
  const [visible, setVisible] = useState(false);
  const today = useMemo(() => new Date(), []);
  const initialDate = useMemo(() => {
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
      return new Date(value + "T00:00:00");
    }
    return today;
  }, [value, today]);

  const [year, setYear] = useState(initialDate.getFullYear());
  const [month, setMonth] = useState(initialDate.getMonth() + 1);
  const [day, setDay] = useState(initialDate.getDate());

  const yearInputRef = useRef<any>(null);

  const openPicker = useCallback(() => {
    const d = /^\d{4}-\d{2}-\d{2}$/.test(value)
      ? new Date(value + "T00:00:00")
      : today;
    setYear(d.getFullYear());
    setMonth(d.getMonth() + 1);
    setDay(d.getDate());
    setVisible(true);
  }, [value, today]);

  const confirm = useCallback(() => {
    const y = String(year).padStart(4, "0");
    const m = String(month).padStart(2, "0");
    const d = String(day).padStart(2, "0");
    onChange(`${y}-${m}-${d}`);
    setVisible(false);
  }, [year, month, day, onChange]);

  const maxDay = daysInMonth(year, month);
  const safeDay = Math.min(day, maxDay);

  return (
    <>
      <TouchableOpacity style={styles.dateButton} onPress={openPicker}>
        <MaterialCommunityIcons
          name="calendar"
          size={18}
          color={colors.accent}
        />
        <Text style={styles.dateText}>{formatDisplay(value)}</Text>
      </TouchableOpacity>

      <Modal visible={visible} transparent animationType="fade">
        <TouchableOpacity
          style={styles.overlay}
          activeOpacity={1}
          onPress={() => setVisible(false)}
        >
          <View
            style={styles.picker}
            onStartShouldSetResponder={() => true}
          >
            <Text style={styles.pickerTitle}>选择日期</Text>

            <View style={styles.pickerRow}>
              <View style={styles.pickerCol}>
                <Text style={styles.pickerLabel}>年</Text>
                <TouchableOpacity
                  style={styles.stepper}
                  onPress={() => setYear((y) => Math.max(2000, y - 1))}
                >
                  <MaterialCommunityIcons name="chevron-up" size={20} color={colors.text} />
                </TouchableOpacity>
                <Text style={styles.pickerValue}>{year}</Text>
                <TouchableOpacity
                  style={styles.stepper}
                  onPress={() => setYear((y) => Math.min(2100, y + 1))}
                >
                  <MaterialCommunityIcons name="chevron-down" size={20} color={colors.text} />
                </TouchableOpacity>
              </View>

              <View style={styles.pickerCol}>
                <Text style={styles.pickerLabel}>月</Text>
                <TouchableOpacity
                  style={styles.stepper}
                  onPress={() => setMonth((m) => (m <= 1 ? 12 : m - 1))}
                >
                  <MaterialCommunityIcons name="chevron-up" size={20} color={colors.text} />
                </TouchableOpacity>
                <Text style={styles.pickerValue}>{month}月</Text>
                <TouchableOpacity
                  style={styles.stepper}
                  onPress={() => setMonth((m) => (m >= 12 ? 1 : m + 1))}
                >
                  <MaterialCommunityIcons name="chevron-down" size={20} color={colors.text} />
                </TouchableOpacity>
              </View>

              <View style={styles.pickerCol}>
                <Text style={styles.pickerLabel}>日</Text>
                <TouchableOpacity
                  style={styles.stepper}
                  onPress={() => setDay((d) => (d <= 1 ? maxDay : d - 1))}
                >
                  <MaterialCommunityIcons name="chevron-up" size={20} color={colors.text} />
                </TouchableOpacity>
                <Text style={styles.pickerValue}>{safeDay}日</Text>
                <TouchableOpacity
                  style={styles.stepper}
                  onPress={() => setDay((d) => (d >= maxDay ? 1 : d + 1))}
                >
                  <MaterialCommunityIcons name="chevron-down" size={20} color={colors.text} />
                </TouchableOpacity>
              </View>
            </View>

            <View style={styles.pickerActions}>
              <TouchableOpacity
                style={styles.cancelButton}
                onPress={() => setVisible(false)}
              >
                <Text style={styles.cancelText}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.confirmButton} onPress={confirm}>
                <Text style={styles.confirmText}>确定</Text>
              </TouchableOpacity>
            </View>
          </View>
        </TouchableOpacity>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  cancelButton: {
    alignItems: "center",
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    flex: 1,
    justifyContent: "center",
    minHeight: 42,
  },
  cancelText: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "600",
  },
  confirmButton: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 8,
    flex: 1,
    justifyContent: "center",
    minHeight: 42,
  },
  confirmText: {
    color: "#FFF",
    fontSize: 15,
    fontWeight: "700",
  },
  dateButton: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: "row",
    gap: 8,
    minHeight: 44,
    paddingHorizontal: 12,
  },
  dateText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "600",
  },
  overlay: {
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.5)",
    flex: 1,
    justifyContent: "center",
    padding: 24,
  },
  picker: {
    backgroundColor: colors.surface,
    borderRadius: 16,
    padding: 24,
    width: "100%",
    maxWidth: 360,
  },
  pickerActions: {
    flexDirection: "row",
    gap: 12,
    marginTop: 24,
  },
  pickerCol: {
    alignItems: "center",
    flex: 1,
    gap: 4,
  },
  pickerLabel: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: "600",
  },
  pickerRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 20,
  },
  pickerTitle: {
    color: colors.text,
    fontSize: 17,
    fontWeight: "800",
    textAlign: "center",
  },
  pickerValue: {
    color: colors.text,
    fontSize: 24,
    fontWeight: "800",
    paddingVertical: 6,
  },
  stepper: {
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 14,
    paddingVertical: 4,
  },
});
