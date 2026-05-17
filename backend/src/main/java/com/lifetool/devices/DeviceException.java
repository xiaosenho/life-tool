package com.lifetool.devices;

public class DeviceException extends RuntimeException {
    private final String code;

    public DeviceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
