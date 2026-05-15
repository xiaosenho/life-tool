package com.lifetool.media;

import com.lifetool.media.dto.AssetResponse;
import com.lifetool.media.dto.CreateAssetRequest;
import com.lifetool.media.dto.UploadTokenRequest;
import com.lifetool.media.dto.UploadTokenResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaService {

    private final MediaAssetStore store;
    private final MediaConfig config;
    private final CosUploadUrlSigner uploadUrlSigner;

    public MediaService(MediaAssetStore store, MediaConfig config, CosUploadUrlSigner uploadUrlSigner) {
        this.store = store;
        this.config = config;
        this.uploadUrlSigner = uploadUrlSigner;
    }

    public UploadTokenResponse generateUploadToken(String userId, UploadTokenRequest req) {
        validateContentType(req.contentType());
        validatePurpose(req.purpose());
        validateFileSize(req.contentType(), req.fileSize());

        String assetId = UUID.randomUUID().toString();
        String ext = extensionFor(req.contentType());
        String objectKey = "users/" + userId + "/media/" + assetId + "." + ext;

        Instant expiresAt = Instant.now().plusSeconds(config.getUploadTokenTtlSeconds());
        String uploadUrl = uploadUrlSigner.generatePutUrl(objectKey, expiresAt);

        return new UploadTokenResponse(
                assetId,
                uploadUrl,
                objectKey,
                expiresAt,
                Map.of("Content-Type", req.contentType()));
    }

    public AssetResponse createAsset(String userId, CreateAssetRequest req) {
        validateContentType(req.contentType());
        validatePurpose(req.purpose());
        validateFileSize(req.contentType(), req.fileSize());

        if (!req.objectKey().startsWith("users/" + userId + "/")) {
            throw new MediaException("FORBIDDEN", "Object key does not belong to current user");
        }
        if (!req.objectKey().startsWith("users/" + userId + "/media/" + req.assetId() + ".")) {
            throw new MediaException("VALIDATION_ERROR", "Object key does not match assetId");
        }

        MediaAsset asset = new MediaAsset(
                req.assetId(), userId, req.objectKey(), req.contentType(),
                req.purpose(), req.fileSize(), req.width(), req.height());
        store.save(asset);

        return toResponse(asset);
    }

    public AssetResponse getAsset(String userId, String assetId) {
        MediaAsset asset = findOwnedAsset(userId, assetId);
        return toResponse(asset);
    }

    public String generateReadUrl(String userId, String assetId, String expectedPurpose) {
        MediaAsset asset = findOwnedAsset(userId, assetId);
        if (expectedPurpose != null && !expectedPurpose.equals(asset.getPurpose())) {
            throw new MediaException("VALIDATION_ERROR", "Media asset purpose does not match expected purpose");
        }
        return buildReadUrl(asset);
    }

    public void deleteAsset(String userId, String assetId) {
        MediaAsset asset = findOwnedAsset(userId, assetId);
        asset.markDeleted();
    }

    public MediaAsset findOwnedAsset(String userId, String assetId) {
        MediaAsset asset = store.findById(assetId)
                .orElseThrow(() -> new MediaException("NOT_FOUND", "Asset not found"));
        if ("deleted".equals(asset.getStatus())) {
            throw new MediaException("NOT_FOUND", "Asset not found");
        }
        if (!asset.getUserId().equals(userId)) {
            throw new MediaException("FORBIDDEN", "Access denied");
        }
        return asset;
    }

    private void validateContentType(String contentType) {
        if (!config.isContentTypeAllowed(contentType)) {
            throw new MediaException("UNSUPPORTED_MEDIA_TYPE",
                    "Unsupported content type: " + contentType);
        }
    }

    private void validatePurpose(String purpose) {
        if (!config.isPurposeAllowed(purpose)) {
            throw new MediaException("VALIDATION_ERROR",
                    "Invalid purpose: " + purpose);
        }
    }

    private void validateFileSize(String contentType, long fileSize) {
        long maxBytes = contentType != null && contentType.startsWith("audio/")
                ? config.getMaxAudioBytes()
                : config.getMaxImageBytes();
        if (fileSize > maxBytes) {
            throw new MediaException("FILE_TOO_LARGE",
                    "File size " + fileSize + " exceeds maximum " + maxBytes + " bytes");
        }
    }

    private void validateFileSize(long fileSize) {
        validateFileSize(null, fileSize);
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "audio/m4a", "audio/mp4" -> "m4a";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/wav" -> "wav";
            default -> "jpg";
        };
    }

    private AssetResponse toResponse(MediaAsset a) {
        return new AssetResponse(
                a.getId(), a.getObjectKey(), a.getContentType(), a.getPurpose(),
                a.getFileSize(), a.getWidth(), a.getHeight(), a.getStatus(), a.getCreatedAt(),
                buildReadUrl(a));
    }

    private String buildReadUrl(MediaAsset asset) {
        if (config.isPublicReadUrlEnabled()) {
            String baseUrl = config.getCosPublicBaseUrl().trim();
            String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return normalizedBaseUrl + "/" + asset.getObjectKey();
        }
        return uploadUrlSigner.generateGetUrl(
                asset.getObjectKey(),
                Instant.now().plusSeconds(config.getReadUrlTtlSeconds()));
    }
}
