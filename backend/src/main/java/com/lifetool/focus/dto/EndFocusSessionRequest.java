package com.lifetool.focus.dto;

public record EndFocusSessionRequest(
        int actualMinutes,
        String status,
        String note) {
}
