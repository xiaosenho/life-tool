package com.lifetool.ai.dto;

import java.time.Instant;
import java.util.UUID;

public class AiJob {

    public enum Status { PENDING, SUCCEEDED, FAILED }

    private String id;
    private String userId;
    private String jobType;
    private String mediaAssetId;
    private Status status;
    private String resultJson;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public AiJob(String userId, String jobType, String mediaAssetId) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.jobType = jobType;
        this.mediaAssetId = mediaAssetId;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getJobType() { return jobType; }
    public String getMediaAssetId() { return mediaAssetId; }
    public Status getStatus() { return status; }
    public String getResultJson() { return resultJson; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(Status status) { this.status = status; this.updatedAt = Instant.now(); }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; this.updatedAt = Instant.now(); }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; this.updatedAt = Instant.now(); }
}
