import { Stack, useLocalSearchParams } from "expo-router";
import { Linking, Pressable, StyleSheet, Text, View } from "react-native";

import { colors } from "@/theme/colors";

export default function NewsWebViewScreen() {
  const params = useLocalSearchParams<{ url?: string; title?: string }>();
  const url = typeof params.url === "string" ? params.url : "";
  const title = typeof params.title === "string" ? params.title : "新闻";

  return (
    <>
      <Stack.Screen options={{ headerShown: true, headerTitle: title }} />
      <View style={styles.container}>
        {url ? (
          <iframe src={url} title={title} style={styles.iframe} />
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyTitle}>新闻地址为空</Text>
            <Text style={styles.emptyDescription}>请返回新闻列表重新打开。</Text>
          </View>
        )}
        {url ? (
          <Pressable style={styles.openButton} onPress={() => Linking.openURL(url)}>
            <Text style={styles.openButtonText}>在新窗口打开</Text>
          </Pressable>
        ) : null}
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  emptyDescription: {
    color: colors.muted,
    fontSize: 14,
    marginTop: 8,
  },
  emptyState: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center",
    padding: 24,
  },
  emptyTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "700",
  },
  iframe: {
    borderWidth: 0,
    flex: 1,
    width: "100%",
  },
  openButton: {
    alignSelf: "center",
    backgroundColor: colors.accent,
    borderRadius: 999,
    bottom: 24,
    paddingHorizontal: 18,
    paddingVertical: 10,
    position: "absolute",
    right: 24,
  },
  openButtonText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "700",
  },
});
