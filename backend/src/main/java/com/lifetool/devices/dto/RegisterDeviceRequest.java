package com.lifetool.devices.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record RegisterDeviceRequest(
        @NotBlank String installationId,
        @NotBlank String deviceName,
        @NotBlank String deviceType,
        String pushToken,
        String vendorDeviceId,
        String pushProvider,
        Boolean pushEnabled,
        Map<String, Object> metadata
) {
}
