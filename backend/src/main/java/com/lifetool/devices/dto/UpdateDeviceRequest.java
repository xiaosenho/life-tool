package com.lifetool.devices.dto;

import java.util.Map;

public record UpdateDeviceRequest(
        String deviceName,
        String pushToken,
        String vendorDeviceId,
        String pushProvider,
        Boolean pushEnabled,
        Map<String, Object> metadata
) {
}
