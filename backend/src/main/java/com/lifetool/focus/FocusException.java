package com.lifetool.focus;

public class FocusException extends RuntimeException {

    private final String code;

    public FocusException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
