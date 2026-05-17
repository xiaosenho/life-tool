import { Platform } from "react-native";

import { apiClient } from "./apiClient";
import { syncService } from "./syncService";

export interface DeviceResponse {
  id: string;
  installationId: string;
  deviceName: string;
  deviceType: "ios" | "android" | "web";
  pushToken: string | null;
  vendorDeviceId: string | null;
  pushProvider: string | null;
  pushEnabled: boolean;
  pushBoundAt: string | null;
  lastActiveAt: string;
  createdAt: string;
  updatedAt: string;
  metadata: Record<string, unknown>;
}

export interface RegisterDevicePayload {
  installationId: string;
  deviceName: string;
  deviceType: "ios" | "android" | "web";
  pushToken?: string | null;
  vendorDeviceId?: string | null;
  pushProvider?: string | null;
  pushEnabled?: boolean;
  metadata?: Record<string, unknown>;
}

export interface UpdateDevicePayload {
  deviceName?: string;
  pushToken?: string | null;
  vendorDeviceId?: string | null;
  pushProvider?: string | null;
  pushEnabled?: boolean;
  metadata?: Record<string, unknown>;
}

export const deviceService = {
  async ensureInstallationId() {
    return syncService.getDeviceId();
  },

  async register(payload: Omit<RegisterDevicePayload, "installationId" | "deviceType"> & { installationId?: string }) {
    const installationId = payload.installationId ?? (await this.ensureInstallationId());
    return apiClient.post<DeviceResponse>("/devices", {
      installationId,
      deviceName: payload.deviceName,
      deviceType: Platform.OS === "ios" ? "ios" : Platform.OS === "android" ? "android" : "web",
      pushToken: payload.pushToken ?? null,
      vendorDeviceId: payload.vendorDeviceId ?? null,
      pushProvider: payload.pushProvider ?? "none",
      pushEnabled: payload.pushEnabled ?? false,
      metadata: payload.metadata ?? {}
    });
  },

  update(deviceId: string, payload: UpdateDevicePayload) {
    return apiClient.patch<DeviceResponse>(`/devices/${deviceId}`, payload);
  },

  list() {
    return apiClient.get<DeviceResponse[]>("/devices");
  }
};
