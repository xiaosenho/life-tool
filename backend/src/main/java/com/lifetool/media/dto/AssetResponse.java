package com.lifetool.media.dto;

import java.time.Instant;

public record AssetResponse(
        String id,
        String objectKey,
        String contentType,
        String purpose,
        Long fileSize,
        Integer width,
        Integer height,
        String status,
        Instant createdAt,
        String readUrl) {
}
