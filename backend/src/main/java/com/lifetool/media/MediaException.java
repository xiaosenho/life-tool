package com.lifetool.media;

public class MediaException extends RuntimeException {

    private final String code;

    public MediaException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
