package com.lifetool.devices;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryDeviceStore implements DeviceStore {

    private final Map<String, Device> devicesById = new ConcurrentHashMap<>();
    private final Map<String, String> deviceIdByInstallationId = new ConcurrentHashMap<>();

    @Override
    public Device save(Device device) {
        devicesById.put(device.getId(), device);
        if (device.getInstallationId() != null && !device.getInstallationId().isBlank()) {
            deviceIdByInstallationId.put(device.getInstallationId(), device.getId());
        }
        return device;
    }

    @Override
    public Optional<Device> findById(String id) {
        return Optional.ofNullable(devicesById.get(id));
    }

    @Override
    public Optional<Device> findByInstallationId(String installationId) {
        String deviceId = deviceIdByInstallationId.get(installationId);
        if (deviceId == null) {
            return Optional.empty();
        }
        return findById(deviceId);
    }

    @Override
    public List<Device> findByUserId(String userId) {
        return devicesById.values().stream()
                .filter(device -> device.getUserId().equals(userId))
                .toList();
    }
}
