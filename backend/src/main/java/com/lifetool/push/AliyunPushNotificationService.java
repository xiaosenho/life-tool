package com.lifetool.push;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetool.devices.Device;
import com.lifetool.devices.DeviceService;

@Service
@ConditionalOnProperty(prefix = "lifetool.push.aliyun", name = "enabled", havingValue = "true")
public class AliyunPushNotificationService implements PushNotificationService {
    private static final Logger log = LoggerFactory.getLogger(AliyunPushNotificationService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AliyunPushProperties properties;
    private final DeviceService deviceService;

    public AliyunPushNotificationService(AliyunPushProperties properties, DeviceService deviceService) {
        this.properties = properties;
        this.deviceService = deviceService;
    }

    @Override
    public void pushToUser(PushNotificationCommand command) {
        if (properties.getAppKey() == null || properties.getAccessKeyId() == null || properties.getAccessKeySecret() == null) {
            log.warn("Aliyun push skipped, missing credentials");
            return;
        }
        for (Device device : deviceService.listPushableDevices(command.targetUserId())) {
            if (device.getVendorDeviceId() == null || device.getVendorDeviceId().isBlank()) {
                continue;
            }
            try {
                Map<String, Object> extras = new HashMap<>();
                if (command.extras() != null) {
                    extras.putAll(command.extras());
                }
                if (command.deepLink() != null && !command.deepLink().isBlank()) {
                    extras.put("deepLink", command.deepLink());
                }
                pushByReflection(device, command, extras);
            } catch (Exception ex) {
                log.warn("Aliyun push failed for userId={}, deviceId={}, message={}",
                        command.targetUserId(), device.getId(), ex.getMessage());
            }
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize push extras", ex);
        }
    }

    private void pushByReflection(Device device, PushNotificationCommand command, Map<String, Object> extras) throws Exception {
        Class<?> asyncClientClass = Class.forName("com.aliyun.sdk.service.push20160801.AsyncClient");
        Object builder = asyncClientClass.getMethod("builder").invoke(null);
        builder.getClass().getMethod("credential", String.class, String.class)
                .invoke(builder, properties.getAccessKeyId(), properties.getAccessKeySecret());
        Object client = builder.getClass().getMethod("build").invoke(builder);
        try {
            Class<?> pushRequestClass = Class.forName("com.aliyun.sdk.service.push20160801.models.PushRequest");
            Object requestBuilder = pushRequestClass.getMethod("builder").invoke(null);
            requestBuilder.getClass().getMethod("appKey", Long.class).invoke(requestBuilder, properties.getAppKey());
            requestBuilder.getClass().getMethod("deviceType", String.class).invoke(requestBuilder, device.getDeviceType().name());
            requestBuilder.getClass().getMethod("pushType", String.class).invoke(requestBuilder, "NOTICE");
            requestBuilder.getClass().getMethod("target", String.class).invoke(requestBuilder, "DEVICE");
            requestBuilder.getClass().getMethod("targetValue", String.class).invoke(requestBuilder, device.getVendorDeviceId());
            requestBuilder.getClass().getMethod("title", String.class).invoke(requestBuilder, command.title());
            requestBuilder.getClass().getMethod("body", String.class).invoke(requestBuilder, command.body());
            requestBuilder.getClass().getMethod("androidOpenType", String.class).invoke(requestBuilder, properties.getAndroidOpenType());
            requestBuilder.getClass().getMethod("androidActivity", String.class).invoke(requestBuilder, properties.getAndroidActivity());
            requestBuilder.getClass().getMethod("androidExtParameters", String.class).invoke(requestBuilder, writeJson(extras));
            requestBuilder.getClass().getMethod("storeOffline", Boolean.class).invoke(requestBuilder, properties.isStoreOffline());
            Object request = requestBuilder.getClass().getMethod("build").invoke(requestBuilder);
            Object future = client.getClass().getMethod("push", pushRequestClass).invoke(client, request);
            future.getClass().getMethod("join").invoke(future);
        } finally {
            client.getClass().getMethod("close").invoke(client);
        }
    }
}
