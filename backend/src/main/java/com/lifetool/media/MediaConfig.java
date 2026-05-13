package com.lifetool.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MediaConfig {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private static final Set<String> ALLOWED_PURPOSES = Set.of(
            "meal_photo", "event_photo", "avatar");

    @Value("${COS_REGION:ap-guangzhou}")
    private String cosRegion;

    @Value("${COS_BUCKET:life-tool-media}")
    private String cosBucket;

    @Value("${COS_SECRET_ID:}")
    private String cosSecretId;

    @Value("${COS_SECRET_KEY:}")
    private String cosSecretKey;

    @Value("${COS_PUBLIC_BASE_URL:}")
    private String cosPublicBaseUrl;

    @Value("${COS_UPLOAD_TOKEN_TTL_SECONDS:300}")
    private int uploadTokenTtlSeconds;

    @Value("${MEDIA_MAX_IMAGE_BYTES:10485760}")
    private long maxImageBytes;

    public String getCosRegion() { return cosRegion; }
    public String getCosBucket() { return cosBucket; }
    public String getCosSecretId() { return cosSecretId; }
    public String getCosSecretKey() { return cosSecretKey; }
    public String getCosPublicBaseUrl() { return cosPublicBaseUrl; }
    public int getUploadTokenTtlSeconds() { return uploadTokenTtlSeconds; }
    public long getMaxImageBytes() { return maxImageBytes; }

    public boolean isCosSigningEnabled() {
        return hasText(cosSecretId) && hasText(cosSecretKey) && hasText(cosBucket) && hasText(cosRegion);
    }

    public boolean isContentTypeAllowed(String contentType) {
        return ALLOWED_CONTENT_TYPES.contains(contentType);
    }

    public boolean isPurposeAllowed(String purpose) {
        return ALLOWED_PURPOSES.contains(purpose);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
