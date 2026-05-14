package com.lifetool.focus.dto;

public record CreateFocusSessionRequest(
        String mode,
        int targetMinutes,
        String note) {
}
