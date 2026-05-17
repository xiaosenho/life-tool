package com.lifetool.devices.dto;

import java.time.Instant;
import java.util.Map;

import com.lifetool.devices.Device;

public record DeviceResponse(
        String id,
        String installationId,
        String deviceName,
        String deviceType,
        String pushToken,
        String vendorDeviceId,
        String pushProvider,
        boolean pushEnabled,
        Instant pushBoundAt,
        Instant lastActiveAt,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> metadata
) {
    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getInstallationId(),
                device.getDeviceName(),
                device.getDeviceType().name().toLowerCase(),
                device.getPushToken(),
                device.getVendorDeviceId(),
                device.getPushProvider() == null ? null : device.getPushProvider().name().toLowerCase(),
                device.isPushEnabled(),
                device.getPushBoundAt(),
                device.getLastActiveAt(),
                device.getCreatedAt(),
                device.getUpdatedAt(),
                device.getMetadata()
        );
    }
}
