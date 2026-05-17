package com.lifetool.devices;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class Device {

    public enum DeviceType {
        IOS,
        ANDROID,
        WEB
    }

    public enum PushProvider {
        ALIYUN,
        FCM,
        APNS,
        NONE
    }

    private final String id;
    private final String userId;
    private final String installationId;
    private final String deviceName;
    private final DeviceType deviceType;
    private final String pushToken;
    private final String vendorDeviceId;
    private final PushProvider pushProvider;
    private final boolean pushEnabled;
    private final Instant pushBoundAt;
    private final Instant lastActiveAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Map<String, Object> metadata;

    public Device(
            String userId,
            String installationId,
            String deviceName,
            DeviceType deviceType,
            String pushToken,
            String vendorDeviceId,
            PushProvider pushProvider,
            boolean pushEnabled,
            Instant pushBoundAt,
            Instant lastActiveAt,
            Map<String, Object> metadata
    ) {
        this(
                UUID.randomUUID().toString(),
                userId,
                installationId,
                deviceName,
                deviceType,
                pushToken,
                vendorDeviceId,
                pushProvider,
                pushEnabled,
                pushBoundAt,
                lastActiveAt == null ? Instant.now() : lastActiveAt,
                Instant.now(),
                Instant.now(),
                metadata
        );
    }

    public Device(
            String id,
            String userId,
            String installationId,
            String deviceName,
            DeviceType deviceType,
            String pushToken,
            String vendorDeviceId,
            PushProvider pushProvider,
            boolean pushEnabled,
            Instant pushBoundAt,
            Instant lastActiveAt,
            Instant createdAt,
            Instant updatedAt,
            Map<String, Object> metadata
    ) {
        this.id = id;
        this.userId = userId;
        this.installationId = installationId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.pushToken = pushToken;
        this.vendorDeviceId = vendorDeviceId;
        this.pushProvider = pushProvider;
        this.pushEnabled = pushEnabled;
        this.pushBoundAt = pushBoundAt;
        this.lastActiveAt = lastActiveAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getInstallationId() {
        return installationId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public String getPushToken() {
        return pushToken;
    }

    public String getVendorDeviceId() {
        return vendorDeviceId;
    }

    public PushProvider getPushProvider() {
        return pushProvider;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public Instant getPushBoundAt() {
        return pushBoundAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
