import React from "react";
import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { colors } from "@/theme/colors";

const DAY_OPTIONS = [1, 3, 7, 14, 30];

interface DayChipSelectorProps {
  selected: number[];
  onChange: (days: number[]) => void;
}

export function DayChipSelector({ selected, onChange }: DayChipSelectorProps) {
  const toggle = (day: number) => {
    if (selected.includes(day)) {
      onChange(selected.filter((d) => d !== day));
    } else {
      onChange([...selected, day].sort((a, b) => a - b));
    }
  };

  return (
    <View style={styles.chipRow}>
      <Text style={styles.label}>提前提醒</Text>
      <View style={styles.chips}>
        {DAY_OPTIONS.map((day) => {
          const active = selected.includes(day);
          return (
            <TouchableOpacity
              key={day}
              style={[styles.chip, active && styles.chipActive]}
              onPress={() => toggle(day)}
            >
              <Text style={[styles.chipText, active && styles.chipTextActive]}>
                {day}天
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    alignItems: "center",
    backgroundColor: colors.background,
    borderColor: colors.border,
    borderRadius: 20,
    borderWidth: 1,
    justifyContent: "center",
    minWidth: 56,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  chipActive: {
    backgroundColor: colors.accent,
    borderColor: colors.accent,
  },
  chipRow: {
    alignItems: "center",
    flexDirection: "row",
    gap: 10,
  },
  chips: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  chipText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "600",
  },
  chipTextActive: {
    color: "#FFF",
  },
  label: {
    color: colors.muted,
    fontSize: 14,
    fontWeight: "600",
  },
});
