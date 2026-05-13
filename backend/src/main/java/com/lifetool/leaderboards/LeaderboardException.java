package com.lifetool.leaderboards;

public class LeaderboardException extends RuntimeException {

    private final String code;

    public LeaderboardException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
