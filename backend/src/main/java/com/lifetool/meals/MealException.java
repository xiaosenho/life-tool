package com.lifetool.meals;

public class MealException extends RuntimeException {
    private final String code;

    public MealException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
