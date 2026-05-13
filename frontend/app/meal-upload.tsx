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
import { mediaService, AssetResponse } from "@/services/mediaService";
import { aiService, FoodResult } from "@/services/aiService";

type UploadStatus = "idle" | "uploading" | "recognizing" | "success" | "error";

export default function MealUploadScreen() {
  const router = useRouter();
  const [image, setImage] = useState<string | null>(null);
  const [status, setStatus] = useState<UploadStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [asset, setAsset] = useState<AssetResponse | null>(null);
  const [foodResult, setFoodResult] = useState<FoodResult | null>(null);

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

      setStatus("recognizing");
      const job = await aiService.createFoodRecognitionJob(result.id);
      const recognition = await aiService.waitForJobCompletion(job.jobId);
      if (recognition.status === "succeeded" && recognition.result) {
        setFoodResult(recognition.result);
        setStatus("success");
      } else {
        setStatus("error");
        setError("AI 识别失败，请重试");
      }
    } catch (err: any) {
      console.error("上传图片失败：", err);
      setStatus("error");
      setError(err.message || "上传失败，请检查网络或配置");
    }
  };

  const reset = () => {
    setImage(null);
    setStatus("idle");
    setError(null);
    setAsset(null);
    setFoodResult(null);
  };

  return (
    <Screen title="饮食拍照" contentContainerStyle={styles.scrollContent}>
      <Stack.Screen options={{ headerTitle: "饮食拍照" }} />
      {!image ? (
        <View style={styles.emptyState}>
          <View style={styles.iconCircle}>
            <MaterialCommunityIcons name="food-apple" size={48} color={colors.accent} />
          </View>
          <Text style={styles.emptyTitle}>记录你的饮食</Text>
          <Text style={styles.emptySubtitle}>拍一张照片，AI 将自动识别食物和热量</Text>

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
            {(status === "uploading" || status === "recognizing") && (
              <View style={styles.overlay}>
                <ActivityIndicator size="large" color="#FFF" />
                <Text style={styles.overlayText}>
                  {status === "uploading" ? "正在上传..." : "AI 识别中..."}
                </Text>
              </View>
            )}
          </View>

          {status === "error" && (
            <View style={styles.errorBox}>
              <MaterialCommunityIcons name="alert-circle" size={24} color={colors.error} />
              <Text style={styles.errorText}>{error}</Text>
              <TouchableOpacity style={styles.retryButton} onPress={reset}>
                <Text style={styles.retryButtonText}>重新选择</Text>
              </TouchableOpacity>
            </View>
          )}

          {status === "success" && foodResult && (
            <View style={styles.successContainer}>
              <View style={styles.successHeader}>
                <MaterialCommunityIcons name="check-circle" size={24} color="green" />
                <Text style={styles.successTitle}>识别完成</Text>
              </View>

              <View style={styles.calorieCard}>
                <Text style={styles.calorieLabel}>估算总热量</Text>
                <Text style={styles.calorieValue}>{foodResult.totalCalories}</Text>
                <Text style={styles.calorieUnit}>千卡</Text>
              </View>

              <View style={styles.foodList}>
                {foodResult.items.map((item, idx) => (
                  <View key={idx} style={styles.foodItem}>
                    <View style={styles.foodInfo}>
                      <Text style={styles.foodName}>{item.name}</Text>
                      <Text style={styles.foodDetail}>
                        {item.estimatedGrams}g · 约 {item.estimatedCalories} 千卡
                      </Text>
                    </View>
                    <Text style={styles.confidence}>
                      {Math.round(item.confidence * 100)}%
                    </Text>
                  </View>
                ))}
              </View>

              <Text style={styles.notes}>{foodResult.notes}</Text>

              <TouchableOpacity
                style={[styles.button, styles.primaryButton, { marginTop: 24 }]}
                onPress={() => router.back()}
              >
                <Text style={styles.primaryButtonText}>确认并返回</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
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
  calorieCard: {
    alignItems: "center",
    backgroundColor: `${colors.accent}10`,
    borderRadius: 16,
    marginTop: 20,
    padding: 24,
  },
  calorieLabel: {
    color: colors.muted,
    fontSize: 14,
  },
  calorieUnit: {
    color: colors.muted,
    fontSize: 14,
    marginTop: 4,
  },
  calorieValue: {
    color: colors.accent,
    fontSize: 48,
    fontWeight: "800",
    marginTop: 8,
  },
  confidence: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: "600",
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
  foodDetail: {
    color: colors.muted,
    fontSize: 12,
    marginTop: 2,
  },
  foodInfo: {
    flex: 1,
  },
  foodItem: {
    alignItems: "center",
    borderBottomWidth: 1,
    borderColor: colors.border,
    flexDirection: "row",
    paddingVertical: 14,
  },
  foodList: {
    marginTop: 16,
    width: "100%",
  },
  foodName: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "600",
  },
  iconCircle: {
    alignItems: "center",
    backgroundColor: `${colors.accent}10`,
    borderRadius: 50,
    height: 100,
    justifyContent: "center",
    width: 100,
  },
  notes: {
    color: colors.muted,
    fontSize: 12,
    fontStyle: "italic",
    marginTop: 16,
    textAlign: "center",
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
