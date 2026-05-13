package com.lifetool.media.dto;

import java.time.Instant;
import java.util.Map;

public record UploadTokenResponse(
        String assetId,
        String uploadUrl,
        String objectKey,
        Instant expiresAt,
        Map<String, String> headers) {
}
