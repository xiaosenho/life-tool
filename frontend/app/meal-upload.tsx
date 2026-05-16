import React, { useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { Stack, useRouter } from "expo-router";
import * as ImagePicker from "expo-image-picker";
import { MaterialCommunityIcons } from "@expo/vector-icons";

import { Screen } from "@/components/Screen";
import { colors } from "@/theme/colors";
import { aiService } from "@/services/aiService";
import { mediaService, AssetResponse } from "@/services/mediaService";
import { formatMealRecognitionText } from "@/utils/mealRecognition";

type UploadStatus = "idle" | "uploading" | "success" | "error";

export default function MealUploadScreen() {
  const router = useRouter();
  const [image, setImage] = useState<string | null>(null);
  const [status, setStatus] = useState<UploadStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [asset, setAsset] = useState<AssetResponse | null>(null);
  const [recognizing, setRecognizing] = useState(false);
  const [recognition, setRecognition] = useState<string | null>(null);
  const [recognitionError, setRecognitionError] = useState<string | null>(null);
  const [recognitionSaved, setRecognitionSaved] = useState<boolean | null>(null);

  const pickImage = async (useCamera: boolean) => {
    try {
      let result;
      if (useCamera) {
        if (Platform.OS === 'web') {
          Alert.alert("暂不支持", "网页端暂不支持直接拍照，请从相册选择。");
          return;
        }
        
        const permissionResult = await ImagePicker.requestCameraPermissionsAsync();
        if (permissionResult.granted === false) {
          Alert.alert("需要权限", "需要相机权限才能拍照。");
          return;
        }
        
        result = await ImagePicker.launchCameraAsync({
          mediaTypes: ImagePicker.MediaTypeOptions.Images,
          allowsEditing: true,
          quality: 0.8,
        });
      } else {
        result = await ImagePicker.launchImageLibraryAsync({
          mediaTypes: ImagePicker.MediaTypeOptions.Images,
          allowsEditing: true,
          quality: 0.8,
        });
      }

      if (!result.canceled && result.assets && result.assets[0]) {
        const selectedAsset = result.assets[0];
        setImage(selectedAsset.uri);
        handleUpload(selectedAsset.uri, {
          width: selectedAsset.width,
          height: selectedAsset.height,
          fileSize: selectedAsset.fileSize || 0,
          type: selectedAsset.mimeType || "image/jpeg",
        });
      }
    } catch (err) {
      console.error("选择图片失败：", err);
      setError("选择图片失败");
    }
  };

  const handleUpload = async (uri: string, metadata: { width: number; height: number; fileSize: number; type: string }) => {
    setStatus("uploading");
    setError(null);
    try {
      const result = await mediaService.uploadImage(uri, {
        purpose: "meal_photo",
        width: metadata.width,
        height: metadata.height,
        fileSize: metadata.fileSize,
        type: metadata.type,
      });
      setAsset(result);
      setStatus("success");
      recognizeMeal(result);
    } catch (err: any) {
      console.error("上传图片失败：", err);
      setStatus("error");
      setError(err.message || "上传失败，请检查网络或配置");
    }
  };

  const recognizeMeal = async (currentAsset: AssetResponse | null = asset) => {
    setRecognizing(true);
    setRecognition(null);
    setRecognitionError(null);
    try {
      if (!currentAsset?.id) {
        throw new Error("图片资产信息缺失，请重新上传图片");
      }

      const response = await aiService.recognizeFood({
        mediaAssetId: currentAsset.id,
        customPrompt: "请用中文识别图片中的食物，估算每种食物的重量、热量、蛋白质、脂肪和碳水，并给出总热量。",
      });
      if (!response.success || !response.data) {
        throw new Error(response.error?.message || "识别失败");
      }
      setRecognition(formatMealRecognitionText(response.data.result));
      setRecognitionSaved(Boolean(response.data.mealLogId));
    } catch (err) {
      console.error("AI 饮食识别失败：", err);
      setRecognitionError(err instanceof Error ? err.message : "识别失败，请稍后重试");
      setRecognitionSaved(null);
    } finally {
      setRecognizing(false);
    }
  };

  const reset = () => {
    setImage(null);
    setStatus("idle");
    setError(null);
    setAsset(null);
    setRecognizing(false);
    setRecognition(null);
    setRecognitionError(null);
    setRecognitionSaved(null);
  };

  return (
    <Screen 
      title="饮食拍照" 
      contentContainerStyle={styles.scrollContent}
    >
      <Stack.Screen options={{ headerTitle: "饮食拍照" }} />
      {!image ? (
        <View style={styles.emptyState}>
          <View style={styles.iconCircle}>
            <MaterialCommunityIcons name="food-apple" size={48} color={colors.accent} />
          </View>
          <Text style={styles.emptyTitle}>记录你的饮食</Text>
          <Text style={styles.emptySubtitle}>拍一张照片，后续将为你识别热量和营养</Text>

          <View style={styles.buttonGroup}>
            {Platform.OS !== 'web' && (
              <TouchableOpacity
                style={[styles.button, styles.primaryButton]}
                onPress={() => pickImage(true)}
              >
                <MaterialCommunityIcons name="camera" size={24} color="#FFF" />
                <Text style={styles.primaryButtonText}>拍照记录</Text>
              </TouchableOpacity>
            )}

            <TouchableOpacity
              style={[styles.button, styles.secondaryButton]}
              onPress={() => pickImage(false)}
            >
              <MaterialCommunityIcons name="image" size={24} color={colors.accent} />
              <Text style={styles.secondaryButtonText}>从相册选择</Text>
            </TouchableOpacity>
          </View>
        </View>
      ) : (
        <View style={styles.uploadingContainer}>
          <View style={styles.previewContainer}>
            <Image source={{ uri: image }} style={styles.preview} />
            {status === "uploading" && (
              <View style={styles.overlay}>
                <ActivityIndicator size="large" color="#FFF" />
                <Text style={styles.overlayText}>正在上传...</Text>
              </View>
            )}
          </View>

          {status === "error" && (
            <View style={styles.errorBox}>
              <MaterialCommunityIcons name="alert-circle" size={24} color={colors.error} />
              <Text style={styles.errorText}>{error}</Text>
              <TouchableOpacity style={styles.retryButton} onPress={() => handleUpload(image, { width: 0, height: 0, fileSize: 0, type: "image/jpeg" })}>
                <Text style={styles.retryButtonText}>重试</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.cancelButton} onPress={reset}>
                <Text style={styles.cancelButtonText}>取消</Text>
              </TouchableOpacity>
            </View>
          )}

          {status === "success" && (
            <View style={styles.successContainer}>
              <View style={styles.successHeader}>
                <MaterialCommunityIcons name="check-circle" size={24} color="green" />
                <Text style={styles.successTitle}>上传成功</Text>
              </View>

              <View style={styles.infoRow}>
                <Text style={styles.infoLabel}>资产编号：</Text>
                <Text style={styles.infoValue}>{asset?.id}</Text>
              </View>

              <View style={styles.aiResultBox}>
                <View style={styles.aiResultHeader}>
                  <MaterialCommunityIcons name="robot" size={22} color={colors.accent} />
                  <Text style={styles.aiResultTitle}>AI 热量估算</Text>
                </View>
                {recognizing && (
                  <View style={styles.recognizingRow}>
                    <ActivityIndicator size="small" color={colors.accent} />
                    <Text style={styles.aiHint}>正在识别食物和估算热量...</Text>
                  </View>
                )}
                {recognitionError && !recognizing && (
                  <>
                    <Text style={styles.aiErrorText}>{recognitionError}</Text>
                    <TouchableOpacity
                      style={styles.retryAiButton}
                      onPress={() => recognizeMeal(asset)}
                    >
                      <Text style={styles.retryAiButtonText}>重新识别</Text>
                    </TouchableOpacity>
                  </>
                )}
                {recognition && !recognizing && (
                  <>
                    <Text style={styles.aiSavedText}>
                      {recognitionSaved ? "已同步到今日饮食记录" : "本次识别未写入饮食记录"}
                    </Text>
                    <Text style={styles.aiResultText}>{recognition}</Text>
                  </>
                )}
              </View>

              <TouchableOpacity
                style={[styles.button, styles.primaryButton, { marginTop: 24 }]}
                onPress={() => router.back()}
              >
                <Text style={styles.primaryButtonText}>返回</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  aiHint: {
    color: colors.muted,
    fontSize: 12,
    marginTop: 4,
    textAlign: "center",
  },
  aiErrorText: {
    color: colors.error,
    fontSize: 13,
    lineHeight: 20,
    marginTop: 12,
  },
  aiResultBox: {
    alignItems: "center",
    backgroundColor: colors.background,
    borderRadius: 12,
    marginTop: 20,
    padding: 18,
    width: "100%",
  },
  aiResultHeader: {
    alignItems: "center",
    flexDirection: "row",
    gap: 8,
    width: "100%",
  },
  aiResultText: {
    color: colors.text,
    fontSize: 14,
    lineHeight: 22,
    marginTop: 12,
    width: "100%",
  },
  aiSavedText: {
    color: colors.accent,
    fontSize: 13,
    fontWeight: "700",
    marginTop: 12,
    width: "100%",
  },
  aiResultTitle: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "700",
  },
  aiText: {
    color: colors.muted,
    fontSize: 14,
    fontWeight: "600",
    marginTop: 8,
  },
  button: {
    alignItems: "center",
    borderRadius: 12,
    flexDirection: "row",
    gap: 8,
    height: 56,
    justifyContent: "center",
    paddingHorizontal: 24,
  },
  buttonGroup: {
    gap: 12,
    marginTop: 32,
    width: "100%",
  },
  cancelButton: {
    marginTop: 8,
    padding: 8,
  },
  cancelButtonText: {
    color: colors.muted,
    fontSize: 14,
  },
  emptySubtitle: {
    color: colors.muted,
    fontSize: 14,
    marginTop: 8,
    textAlign: "center",
  },
  emptyState: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center",
    paddingTop: 40,
  },
  emptyTitle: {
    color: colors.text,
    fontSize: 20,
    fontWeight: "700",
    marginTop: 24,
  },
  errorBox: {
    alignItems: "center",
    backgroundColor: `${colors.error}10`,
    borderRadius: 12,
    marginTop: 20,
    padding: 20,
    width: "100%",
  },
  errorText: {
    color: colors.error,
    fontSize: 14,
    marginVertical: 12,
    textAlign: "center",
  },
  iconCircle: {
    alignItems: "center",
    backgroundColor: `${colors.accent}10`,
    borderRadius: 50,
    height: 100,
    justifyContent: "center",
    width: 100,
  },
  infoLabel: {
    color: colors.muted,
    fontSize: 12,
  },
  infoRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 8,
  },
  infoValue: {
    color: colors.text,
    fontSize: 12,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  overlay: {
    ...StyleSheet.absoluteFill,
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.5)",
    justifyContent: "center",
  },
  overlayText: {
    color: "#FFF",
    fontSize: 14,
    fontWeight: "600",
    marginTop: 12,
  },
  preview: {
    borderRadius: 16,
    height: "100%",
    width: "100%",
  },
  previewContainer: {
    aspectRatio: 1,
    borderRadius: 16,
    overflow: "hidden",
    width: "100%",
  },
  primaryButton: {
    backgroundColor: colors.accent,
  },
  primaryButtonText: {
    color: "#FFF",
    fontSize: 16,
    fontWeight: "600",
  },
  retryButton: {
    backgroundColor: colors.error,
    borderRadius: 8,
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  retryButtonText: {
    color: "#FFF",
    fontSize: 14,
    fontWeight: "600",
  },
  retryAiButton: {
    borderColor: colors.accent,
    borderRadius: 8,
    borderWidth: 1,
    marginTop: 12,
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  retryAiButtonText: {
    color: colors.accent,
    fontSize: 13,
    fontWeight: "600",
  },
  recognizingRow: {
    alignItems: "center",
    flexDirection: "row",
    gap: 8,
    marginTop: 14,
    width: "100%",
  },
  scrollContent: {
    paddingBottom: 20,
  },
  secondaryButton: {
    backgroundColor: "#FFF",
    borderColor: colors.accent,
    borderWidth: 1,
  },
  secondaryButtonText: {
    color: colors.accent,
    fontSize: 16,
    fontWeight: "600",
  },
  successContainer: {
    marginTop: 20,
    width: "100%",
  },
  successHeader: {
    alignItems: "center",
    flexDirection: "row",
    gap: 8,
  },
  successTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "700",
  },
  uploadingContainer: {
    alignItems: "center",
    width: "100%",
  },
});
