package com.lifetool.friends;

public class FriendException extends RuntimeException {

    private final String code;

    public FriendException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
