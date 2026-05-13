package com.lifetool.ai;

public class AiException extends RuntimeException {
    private final String code;

    public AiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
