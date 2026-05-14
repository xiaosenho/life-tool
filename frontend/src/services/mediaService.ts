import { apiClient } from "./apiClient";
export interface UploadTokenResponse {
  assetId: string;
  uploadUrl: string;
  objectKey: string;
  expiresAt: string;
  headers: Record<string, string>;
}

export interface AssetResponse {
  id: string;
  objectKey: string;
  contentType: string;
  purpose: string;
  fileSize: number;
  width?: number;
  height?: number;
  status: string;
  createdAt: string;
  readUrl?: string;
}

export interface MediaAssetMetadata {
  assetId: string;
  objectKey: string;
  contentType: string;
  purpose: string;
  fileSize: number;
  width?: number;
  height?: number;
}

export const mediaService = {
  async getUploadToken(contentType: string, purpose: string, fileSize: number) {
    return apiClient.post<UploadTokenResponse>("/media/upload-token", {
      contentType,
      purpose,
      fileSize,
    });
  },

  async readImageBlob(fileUri: string) {
    const response = await fetch(fileUri);
    if (!response.ok) {
      throw new Error("读取图片失败");
    }
    return response.blob();
  },

  async uploadToUrl(url: string, blob: Blob, contentType: string, headers: Record<string, string> = {}) {
    if (url.includes("/mock-cos/")) {
      return true;
    }

    try {
      const uploadResponse = await fetch(url, {
        method: "PUT",
        body: blob,
        headers: {
          "Content-Type": contentType,
          ...headers,
        },
      });

      if (!uploadResponse.ok) {
        throw new Error(`图片上传失败，状态码：${uploadResponse.status}`);
      }

      return true;
    } catch (error) {
      console.error("直传图片失败：", error);
      throw error;
    }
  },

  async saveAsset(metadata: MediaAssetMetadata) {
    return apiClient.post<AssetResponse>("/media/assets", metadata);
  },

  /**
   * Orchestrates the full upload flow
   */
  async uploadImage(uri: string, options: { purpose: string; width?: number; height?: number; fileSize?: number; type?: string }) {
    const { purpose, width, height, fileSize = 0, type = "image/jpeg" } = options;
    const blob = await this.readImageBlob(uri);
    const resolvedFileSize = fileSize > 0 ? fileSize : Math.max(blob.size, 1);
    const resolvedType = blob.type || type;

    // 1. Get token
    const tokenRes = await this.getUploadToken(resolvedType, purpose, resolvedFileSize);
    if (!tokenRes.success || !tokenRes.data) {
      throw new Error(tokenRes.error?.message || "获取上传授权失败");
    }

    const { uploadUrl, assetId, objectKey, headers } = tokenRes.data;

    // 2. Upload to URL
    await this.uploadToUrl(uploadUrl, blob, resolvedType, headers);

    // 3. Save metadata
    const saveRes = await this.saveAsset({
      assetId,
      objectKey,
      contentType: resolvedType,
      purpose,
      fileSize: resolvedFileSize,
      width,
      height,
    });

    if (!saveRes.success || !saveRes.data) {
      throw new Error(saveRes.error?.message || "保存图片信息失败");
    }

    return saveRes.data;
  }
};
