package com.lifetool.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lifetool.push.aliyun")
public class AliyunPushProperties {
    private boolean enabled;
    private String accessKeyId;
    private String accessKeySecret;
    private Long appKey;
    private String androidActivity = "com.anonymous.lifetool.MainActivity";
    private String androidOpenType = "ACTIVITY";
    private boolean storeOffline = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public Long getAppKey() {
        return appKey;
    }

    public void setAppKey(Long appKey) {
        this.appKey = appKey;
    }

    public String getAndroidActivity() {
        return androidActivity;
    }

    public void setAndroidActivity(String androidActivity) {
        this.androidActivity = androidActivity;
    }

    public String getAndroidOpenType() {
        return androidOpenType;
    }

    public void setAndroidOpenType(String androidOpenType) {
        this.androidOpenType = androidOpenType;
    }

    public boolean isStoreOffline() {
        return storeOffline;
    }

    public void setStoreOffline(boolean storeOffline) {
        this.storeOffline = storeOffline;
    }
}
