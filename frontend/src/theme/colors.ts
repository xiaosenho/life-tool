import { reloadAppAsync } from "expo";

import { themeStorage } from "./themeStorage";

export type AppThemeKey = "minimal" | "instagram" | "xiaohongshu";

export interface ThemeColors {
  accent: string;
  background: string;
  border: string;
  muted: string;
  surface: string;
  text: string;
  error: string;
}

const THEME_STORAGE_KEY = "app_theme";

export const themePalettes: Record<AppThemeKey, ThemeColors> = {
  minimal: {
    accent: "#0F766E",
    background: "#F8FAFC",
    border: "#E2E8F0",
    muted: "#64748B",
    surface: "#FFFFFF",
    text: "#0F172A",
    error: "#EF4444"
  },
  instagram: {
    accent: "#C65D72",
    background: "#FAF7F4",
    border: "#E9DFDA",
    muted: "#786D69",
    surface: "#FFFFFF",
    text: "#2C2525",
    error: "#D94C5C"
  },
  xiaohongshu: {
    accent: "#FF2442",
    background: "#F7F7F7",
    border: "#EAEAEA",
    muted: "#737373",
    surface: "#FFFFFF",
    text: "#222222",
    error: "#FF2442"
  }
};

export const appThemeOptions: Array<{
  key: AppThemeKey;
  name: string;
}> = [
  { key: "minimal", name: "清新薄荷" },
  { key: "instagram", name: "暖调玫瑰" },
  { key: "xiaohongshu", name: "元气红白" }
];

function isThemeKey(value: string | null): value is AppThemeKey {
  return value === "minimal" || value === "instagram" || value === "xiaohongshu";
}

function readStoredTheme(): AppThemeKey {
  try {
    const value = themeStorage.getItem(THEME_STORAGE_KEY);
    return isThemeKey(value) ? value : "minimal";
  } catch {
    return "minimal";
  }
}

export const currentThemeKey = readStoredTheme();
export const colors = themePalettes[currentThemeKey];

export async function applyAppTheme(themeKey: AppThemeKey) {
  if (themeKey === currentThemeKey) return;

  themeStorage.setItem(THEME_STORAGE_KEY, themeKey);
  await reloadAppAsync("Apply app theme");
}
