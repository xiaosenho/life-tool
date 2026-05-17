package com.lifetool.devices;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lifetool.devices.dto.DeviceResponse;
import com.lifetool.devices.dto.RegisterDeviceRequest;
import com.lifetool.devices.dto.UpdateDeviceRequest;

@Service
public class DeviceService {

    private final DeviceStore deviceStore;

    public DeviceService(DeviceStore deviceStore) {
        this.deviceStore = deviceStore;
    }

    public DeviceResponse register(String userId, RegisterDeviceRequest request) {
        Device existing = deviceStore.findByInstallationId(request.installationId()).orElse(null);
        Device device = new Device(
                existing == null ? java.util.UUID.randomUUID().toString() : existing.getId(),
                userId,
                request.installationId(),
                normalizeDeviceName(request.deviceName()),
                parseDeviceType(request.deviceType()),
                blankToNull(request.pushToken()),
                blankToNull(request.vendorDeviceId()),
                parsePushProvider(request.pushProvider()),
                Boolean.TRUE.equals(request.pushEnabled()) && hasPushIdentity(request.pushToken(), request.vendorDeviceId()),
                Boolean.TRUE.equals(request.pushEnabled()) && hasPushIdentity(request.pushToken(), request.vendorDeviceId()) ? Instant.now() : null,
                Instant.now(),
                existing == null ? Instant.now() : existing.getCreatedAt(),
                Instant.now(),
                request.metadata() == null ? Map.of() : request.metadata()
        );
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new DeviceException("FORBIDDEN", "installation already belongs to another user");
        }
        return DeviceResponse.from(deviceStore.save(device));
    }

    public DeviceResponse update(String userId, String deviceId, UpdateDeviceRequest request) {
        Device current = deviceStore.findById(deviceId)
                .orElseThrow(() -> new DeviceException("NOT_FOUND", "device not found"));
        if (!current.getUserId().equals(userId)) {
            throw new DeviceException("FORBIDDEN", "device not found");
        }
        String pushToken = request.pushToken() == null ? current.getPushToken() : blankToNull(request.pushToken());
        String vendorDeviceId = request.vendorDeviceId() == null ? current.getVendorDeviceId() : blankToNull(request.vendorDeviceId());
        boolean pushEnabled = request.pushEnabled() == null ? current.isPushEnabled() : request.pushEnabled() && hasPushIdentity(pushToken, vendorDeviceId);
        Device updated = new Device(
                current.getId(),
                current.getUserId(),
                current.getInstallationId(),
                request.deviceName() == null ? current.getDeviceName() : normalizeDeviceName(request.deviceName()),
                current.getDeviceType(),
                pushToken,
                vendorDeviceId,
                request.pushProvider() == null ? current.getPushProvider() : parsePushProvider(request.pushProvider()),
                pushEnabled,
                pushEnabled ? (current.getPushBoundAt() == null ? Instant.now() : current.getPushBoundAt()) : null,
                Instant.now(),
                current.getCreatedAt(),
                Instant.now(),
                request.metadata() == null ? current.getMetadata() : request.metadata()
        );
        return DeviceResponse.from(deviceStore.save(updated));
    }

    public List<DeviceResponse> listByUser(String userId) {
        return deviceStore.findByUserId(userId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    public boolean hasPushableDevice(String userId) {
        return deviceStore.findByUserId(userId).stream()
                .anyMatch(device -> device.isPushEnabled() && hasPushIdentity(device.getPushToken(), device.getVendorDeviceId()));
    }

    public List<Device> listPushableDevices(String userId) {
        return deviceStore.findByUserId(userId).stream()
                .filter(device -> device.isPushEnabled() && hasPushIdentity(device.getPushToken(), device.getVendorDeviceId()))
                .toList();
    }

    private String normalizeDeviceName(String deviceName) {
        String normalized = deviceName == null ? "" : deviceName.trim();
        if (normalized.isBlank()) {
            throw new DeviceException("VALIDATION_ERROR", "deviceName is required");
        }
        return normalized;
    }

    private Device.DeviceType parseDeviceType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DeviceException("VALIDATION_ERROR", "deviceType is required");
        }
        try {
            return Device.DeviceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DeviceException("VALIDATION_ERROR", "unsupported deviceType");
        }
    }

    private Device.PushProvider parsePushProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            return Device.PushProvider.NONE;
        }
        try {
            return Device.PushProvider.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DeviceException("VALIDATION_ERROR", "unsupported pushProvider");
        }
    }

    private boolean hasPushIdentity(String pushToken, String vendorDeviceId) {
        return (pushToken != null && !pushToken.isBlank()) || (vendorDeviceId != null && !vendorDeviceId.isBlank());
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
