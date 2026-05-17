import { NativeModules, Platform } from "react-native";

type AliyunPushModule = {
  getRegistrationInfo(): Promise<{
    vendorDeviceId?: string | null;
    pushToken?: string | null;
    provider?: string | null;
    initialized?: boolean;
  }>;
};

const nativeModule = NativeModules.LifeToolPushModule as AliyunPushModule | undefined;

export const nativePushService = {
  async getRegistrationInfo() {
    if (Platform.OS !== "android" || !nativeModule?.getRegistrationInfo) {
      return {
        vendorDeviceId: null,
        pushToken: null,
        provider: "none",
        initialized: false
      };
    }
    try {
      const info = await nativeModule.getRegistrationInfo();
      return {
        vendorDeviceId: info?.vendorDeviceId ?? null,
        pushToken: info?.pushToken ?? null,
        provider: info?.provider ?? "aliyun",
        initialized: !!info?.initialized
      };
    } catch (error) {
      console.warn("获取阿里云推送注册信息失败", error);
      return {
        vendorDeviceId: null,
        pushToken: null,
        provider: "aliyun",
        initialized: false
      };
    }
  }
};
