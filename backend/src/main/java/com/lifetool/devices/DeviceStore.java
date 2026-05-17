package com.lifetool.devices;

import java.util.List;
import java.util.Optional;

public interface DeviceStore {
    Device save(Device device);

    Optional<Device> findById(String id);

    Optional<Device> findByInstallationId(String installationId);

    List<Device> findByUserId(String userId);
}
