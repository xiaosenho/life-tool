package com.lifetool.vocab;

public class VocabException extends RuntimeException {
    private final String code;

    public VocabException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
