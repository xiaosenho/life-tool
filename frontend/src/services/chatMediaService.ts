import * as ImagePicker from "expo-image-picker";
import { Platform } from "react-native";
import { Audio } from "expo-av";
import * as FileSystem from "expo-file-system";

import { mediaService, AssetResponse } from "./mediaService";

export interface UploadedChatAttachment {
  assetId: string;
  kind: "image" | "audio";
  contentType: string;
  url?: string;
  width?: number;
  height?: number;
  durationSeconds?: number;
}

export async function pickChatImage() {
  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ImagePicker.MediaTypeOptions.Images,
    allowsEditing: false,
    quality: 0.9
  });
  if (result.canceled || !result.assets?.[0]) {
    return null;
  }
  return result.assets[0];
}

export async function uploadChatImage(asset: {
  uri: string;
  width?: number;
  height?: number;
  fileSize?: number;
  mimeType?: string;
}): Promise<UploadedChatAttachment> {
  const uploaded = await mediaService.uploadFile(asset.uri, {
    purpose: "chat_image",
    width: asset.width,
    height: asset.height,
    fileSize: asset.fileSize,
    type: asset.mimeType || "image/jpeg"
  });
  return {
    assetId: uploaded.id,
    kind: "image",
    contentType: uploaded.contentType,
    url: uploaded.readUrl,
    width: uploaded.width,
    height: uploaded.height
  };
}

export async function uploadChatAudio(asset: {
  uri: string;
  fileSize?: number;
  mimeType?: string;
  durationSeconds?: number;
}): Promise<UploadedChatAttachment> {
  const uploaded = await mediaService.uploadFile(asset.uri, {
    purpose: "chat_audio",
    fileSize: asset.fileSize,
    type: asset.mimeType || (Platform.OS === "ios" ? "audio/m4a" : "audio/mp4")
  });
  return {
    assetId: uploaded.id,
    kind: "audio",
    contentType: uploaded.contentType,
    url: uploaded.readUrl,
    durationSeconds: asset.durationSeconds
  };
}

export async function requestAudioPermission() {
  const permission = await Audio.requestPermissionsAsync();
  return permission.granted;
}

export async function startAudioRecording() {
  await Audio.setAudioModeAsync({
    allowsRecordingIOS: true,
    playsInSilentModeIOS: true
  });
  const recording = new Audio.Recording();
  await recording.prepareToRecordAsync(Audio.RecordingOptionsPresets.HIGH_QUALITY);
  await recording.startAsync();
  return recording;
}

export async function stopAudioRecording(recording: Audio.Recording) {
  await recording.stopAndUnloadAsync();
  const uri = recording.getURI();
  const status = await recording.getStatusAsync();
  if (!uri) {
    throw new Error("录音文件生成失败");
  }
  const info = await FileSystem.getInfoAsync(uri);
  return {
    uri,
    durationSeconds: Math.max(1, Math.round((status.durationMillis ?? 0) / 1000)),
    fileSize: info.exists && "size" in info ? info.size ?? 0 : 0
  };
}

export function toAttachmentPayload(attachment?: UploadedChatAttachment | null) {
  if (!attachment) {
    return undefined;
  }
  return {
    assetId: attachment.assetId,
    width: attachment.width,
    height: attachment.height,
    durationSeconds: attachment.durationSeconds
  };
}
