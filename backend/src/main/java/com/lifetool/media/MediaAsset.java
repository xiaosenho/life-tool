package com.lifetool.media;

import java.time.Instant;

public class MediaAsset {

    private final String id;
    private final String userId;
    private final String objectKey;
    private final String contentType;
    private final String purpose;
    private final long fileSize;
    private final Integer width;
    private final Integer height;
    private String status;
    private final Instant createdAt;

    public MediaAsset(String id, String userId, String objectKey, String contentType,
                      String purpose, long fileSize, Integer width, Integer height) {
        this.id = id;
        this.userId = userId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.purpose = purpose;
        this.fileSize = fileSize;
        this.width = width;
        this.height = height;
        this.status = "uploaded";
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public String getPurpose() { return purpose; }
    public long getFileSize() { return fileSize; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void markDeleted() { this.status = "deleted"; }
}
