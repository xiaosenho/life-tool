package com.lifetool.habits;

public class HabitException extends RuntimeException {

    private final String code;

    public HabitException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
