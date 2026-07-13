import { Alert, Linking, Platform } from "react-native";
import Constants from "expo-constants";

import { apiClient } from "./apiClient";

export const CURRENT_APP_VERSION = Constants.expoConfig?.version ?? "1.0.0";
export const CURRENT_APP_VERSION_CODE = Constants.expoConfig?.android?.versionCode ?? 1;

export interface AppRelease {
  platform: "android";
  versionName: string;
  versionCode: number;
  downloadUrl: string;
  releaseNotes: string;
  forceUpdate: boolean;
}

export interface AppUpdateCheckResult {
  currentVersion: string;
  updateAvailable: boolean;
  release: AppRelease;
}

function compareVersions(left: string, right: string) {
  const normalize = (value: string) =>
    value
      .split("-")[0]
      .split(".")
      .map((part) => Number.parseInt(part, 10) || 0);
  const leftParts = normalize(left);
  const rightParts = normalize(right);
  const length = Math.max(leftParts.length, rightParts.length);

  for (let index = 0; index < length; index += 1) {
    const difference = (leftParts[index] ?? 0) - (rightParts[index] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

async function checkForUpdate(): Promise<AppUpdateCheckResult> {
  const response = await apiClient.get<AppRelease>("/app/releases/latest");
  if (!response.success || !response.data) {
    throw new Error(response.error?.message || "无法获取最新版本信息");
  }

  return {
    currentVersion: CURRENT_APP_VERSION,
    updateAvailable:
      response.data.versionCode > CURRENT_APP_VERSION_CODE ||
      compareVersions(response.data.versionName, CURRENT_APP_VERSION) > 0,
    release: response.data
  };
}

async function openDownload(release: AppRelease) {
  if (!release.downloadUrl) {
    throw new Error("服务端尚未配置安装包下载地址");
  }
  await Linking.openURL(release.downloadUrl);
}

function promptForUpdate(release: AppRelease) {
  const buttons = release.forceUpdate
    ? [
        {
          text: "立即更新",
          onPress: () => {
            void openDownload(release).catch((error) => {
              Alert.alert("无法下载更新", error instanceof Error ? error.message : "请稍后重试");
            });
          }
        }
      ]
    : [
        { text: "稍后", style: "cancel" as const },
        {
          text: "立即更新",
          onPress: () => {
            void openDownload(release).catch((error) => {
              Alert.alert("无法下载更新", error instanceof Error ? error.message : "请稍后重试");
            });
          }
        }
      ];

  Alert.alert(
    `发现新版本 ${release.versionName}`,
    release.releaseNotes || "建议更新到最新版本。",
    buttons,
    { cancelable: !release.forceUpdate }
  );
}

export const appUpdateService = {
  isSupported: Platform.OS === "android",
  checkForUpdate,
  openDownload,
  promptForUpdate
};
