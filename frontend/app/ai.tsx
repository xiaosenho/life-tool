import { Ionicons } from "@expo/vector-icons";
import { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  StyleSheet,
  Text,
  TouchableOpacity,
  View
} from "react-native";
import { router } from "expo-router";

import { Screen } from "@/components/Screen";
import { aiService, LifeAdvice, MemoryItem } from "@/services/aiService";
import { colors } from "@/theme/colors";

export default function AiScreen() {
  const [advice, setAdvice] = useState<LifeAdvice | null>(null);
  const [memories, setMemories] = useState<MemoryItem[]>([]);
  const [adviceLoading, setAdviceLoading] = useState(true);
  const [memoriesLoading, setMemoriesLoading] = useState(true);

  const loadAdvice = useCallback(async () => {
    setAdviceLoading(true);
    try {
      const response = await aiService.getLifeAdvice();
      if (response.success && response.data) {
        setAdvice(response.data);
      } else {
        Alert.alert("刷新失败", response.error?.message ?? "请稍后重试。");
      }
    } catch (error) {
      Alert.alert("刷新失败", error instanceof Error ? error.message : "请稍后重试。");
    } finally {
      setAdviceLoading(false);
    }
  }, []);

  const loadMemories = useCallback(async () => {
    setMemoriesLoading(true);
    try {
      const response = await aiService.getMemories();
      if (response.success && response.data) {
        setMemories(response.data.items);
      } else {
        Alert.alert("加载失败", response.error?.message ?? "请稍后重试。");
      }
    } catch (error) {
      Alert.alert("加载失败", error instanceof Error ? error.message : "请稍后重试。");
    } finally {
      setMemoriesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAdvice();
    void loadMemories();
  }, [loadAdvice, loadMemories]);

  const deleteMemory = async (id: string) => {
    const response = await aiService.deleteMemory(id);
    if (response.success) {
      setMemories((current) => current.filter((item) => item.id !== id));
    } else {
      Alert.alert("删除失败", response.error?.message ?? "请稍后重试。");
    }
  };

  return (
    <Screen title="AI">
      <View style={styles.advicePanel}>
        <View style={styles.panelHeader}>
          <Text style={styles.panelTitle}>近期建议</Text>
          {adviceLoading ? (
            <ActivityIndicator size="small" color={colors.accent} />
          ) : (
            <TouchableOpacity style={styles.smallButton} onPress={() => void loadAdvice()}>
              <Ionicons name="refresh" size={18} color={colors.accent} />
            </TouchableOpacity>
          )}
        </View>
        <Text style={styles.summary}>
          {advice?.summary ?? "正在整理你的近期专注、饮食、记账和提醒数据。"}
        </Text>
        {(advice?.suggestions ?? ["建议生成中，稍后会按你的最新数据更新。"]).map((item) => (
          <View key={item} style={styles.suggestionRow}>
            <View style={styles.dot} />
            <Text style={styles.suggestionText}>{item}</Text>
          </View>
        ))}
        <Text style={styles.disclaimer}>
          {advice?.disclaimer ?? "AI 建议仅供参考，不构成医疗、营养、财务或法律结论。"}
        </Text>
      </View>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>对话</Text>
      </View>
      <TouchableOpacity style={styles.entryCard} onPress={() => router.push("/ai-chat")}>
        <View style={styles.entryIcon}>
          <Ionicons name="chatbubble-ellipses-outline" size={22} color={colors.accent} />
        </View>
        <View style={styles.entryTextBlock}>
          <Text style={styles.entryTitle}>打开新对话页面</Text>
          <Text style={styles.entrySubtitle}>在独立页面里提问、查看回复和工具状态</Text>
        </View>
        <Ionicons name="chevron-forward" size={20} color={colors.muted} />
      </TouchableOpacity>

      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>长期记忆</Text>
        <View style={styles.memoryActions}>
          <Text style={styles.sectionMeta}>{memories.length} 条</Text>
          {memoriesLoading ? (
            <ActivityIndicator size="small" color={colors.accent} />
          ) : (
            <TouchableOpacity style={styles.memoryRefresh} onPress={() => void loadMemories()}>
              <Ionicons name="refresh" size={15} color={colors.muted} />
            </TouchableOpacity>
          )}
        </View>
      </View>
      <View style={styles.memoryList}>
        {memories.length === 0 ? (
          <Text style={styles.emptyText}>
            {memoriesLoading ? "正在读取长期记忆..." : "还没有长期记忆。"}
          </Text>
        ) : (
          memories.map((memory) => (
            <View key={memory.id} style={styles.memoryItem}>
              <View style={styles.memoryContent}>
                <Text style={styles.memoryType}>{memory.type}</Text>
                <Text style={styles.memoryText}>{memory.content}</Text>
              </View>
              <TouchableOpacity style={styles.iconButton} onPress={() => void deleteMemory(memory.id)}>
                <Ionicons name="trash-outline" size={18} color={colors.error} />
              </TouchableOpacity>
            </View>
          ))
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  advicePanel: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    gap: 10,
    padding: 16
  },
  disclaimer: {
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18
  },
  dot: {
    backgroundColor: colors.accent,
    borderRadius: 999,
    height: 6,
    marginTop: 8,
    width: 6
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 22
  },
  entryCard: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: "row",
    gap: 12,
    marginTop: 10,
    padding: 14
  },
  entryIcon: {
    alignItems: "center",
    backgroundColor: "#ECFDF5",
    borderRadius: 10,
    height: 42,
    justifyContent: "center",
    width: 42
  },
  entrySubtitle: {
    color: colors.muted,
    fontSize: 13,
    lineHeight: 18
  },
  entryTextBlock: {
    flex: 1,
    gap: 2
  },
  entryTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700"
  },
  iconButton: {
    padding: 6
  },
  memoryActions: {
    alignItems: "center",
    flexDirection: "row",
    gap: 8
  },
  memoryContent: {
    flex: 1,
    gap: 4
  },
  memoryItem: {
    alignItems: "center",
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: "row",
    gap: 10,
    padding: 12
  },
  memoryList: {
    gap: 10,
    marginTop: 10
  },
  memoryRefresh: {
    padding: 4
  },
  memoryText: {
    color: colors.text,
    fontSize: 13,
    lineHeight: 20
  },
  memoryType: {
    color: colors.accent,
    fontSize: 12,
    fontWeight: "700",
    textTransform: "uppercase"
  },
  panelHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between"
  },
  panelTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "800"
  },
  sectionHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 18
  },
  sectionMeta: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: "700"
  },
  sectionTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "800"
  },
  smallButton: {
    alignItems: "center",
    backgroundColor: "#ECFDF5",
    borderRadius: 999,
    height: 34,
    justifyContent: "center",
    width: 34
  },
  suggestionRow: {
    alignItems: "flex-start",
    flexDirection: "row",
    gap: 8
  },
  suggestionText: {
    color: colors.text,
    flex: 1,
    fontSize: 14,
    lineHeight: 22
  },
  summary: {
    color: colors.text,
    fontSize: 14,
    lineHeight: 22
  }
});
