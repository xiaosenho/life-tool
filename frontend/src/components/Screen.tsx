import type { PropsWithChildren } from "react";
import { RefreshControl, ScrollView, StyleSheet, Text, View, ViewStyle } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { colors } from "@/theme/colors";

type ScreenProps = PropsWithChildren<{
  title?: string;
  style?: ViewStyle;
  contentContainerStyle?: ViewStyle;
  scrollable?: boolean;
  refreshControl?: React.ReactElement<any>;
}>;

export function Screen({ children, title, style, contentContainerStyle, scrollable = true, refreshControl }: ScreenProps) {
  const content = (
    <>
      {title && (
        <View style={styles.header}>
          <Text style={styles.title}>{title}</Text>
        </View>
      )}
      {children}
    </>
  );

  return (
    <SafeAreaView style={[styles.safeArea, style]}>
      {scrollable ? (
        <ScrollView refreshControl={refreshControl} contentContainerStyle={[styles.scrollContent, contentContainerStyle]}>
          {content}
        </ScrollView>
      ) : (
        <View style={[styles.scrollContent, contentContainerStyle, { flex: 1 }]}>
          {content}
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingBottom: 18
  },
  safeArea: {
    backgroundColor: colors.background,
    flex: 1
  },
  scrollContent: {
    paddingBottom: 24,
    paddingHorizontal: 18,
    paddingTop: 18
  },
  title: {
    color: colors.text,
    fontSize: 30,
    fontWeight: "800"
  }
});
