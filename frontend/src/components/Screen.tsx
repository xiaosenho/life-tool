import type { PropsWithChildren, ReactNode } from "react";
import { RefreshControl, ScrollView, StyleSheet, Text, View, ViewStyle } from "react-native";
import type { Edge } from "react-native-safe-area-context";
import { SafeAreaView } from "react-native-safe-area-context";

import { colors } from "@/theme/colors";

type ScreenProps = PropsWithChildren<{
  title?: string;
  headerRight?: ReactNode;
  style?: ViewStyle;
  contentContainerStyle?: ViewStyle;
  scrollable?: boolean;
  refreshControl?: React.ReactElement<any>;
  edges?: Edge[];
}>;

export function Screen({
  children,
  title,
  headerRight,
  style,
  contentContainerStyle,
  scrollable = true,
  refreshControl,
  edges = ["top", "right", "bottom", "left"],
}: ScreenProps) {
  const content = (
    <>
      {title && (
        <View style={styles.header}>
          <Text style={styles.title}>{title}</Text>
          {headerRight ? <View style={styles.headerRight}>{headerRight}</View> : null}
        </View>
      )}
      {children}
    </>
  );

  return (
    <SafeAreaView edges={edges} style={[styles.safeArea, style]}>
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
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    gap: 12,
    paddingBottom: 18
  },
  headerRight: {
    flexShrink: 0
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
    flex: 1,
    fontSize: 30,
    fontWeight: "800"
  }
});
