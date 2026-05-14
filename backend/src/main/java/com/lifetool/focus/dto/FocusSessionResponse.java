package com.lifetool.focus.dto;

import java.time.Instant;

import com.lifetool.focus.FocusSession;

public record FocusSessionResponse(
        String id,
        String userId,
        String mode,
        int targetSeconds,
        int actualSeconds,
        String status,
        Instant startedAt,
        Instant endedAt,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    public static FocusSessionResponse from(FocusSession session) {
        return new FocusSessionResponse(
                session.getId(),
                session.getUserId(),
                session.getMode(),
                session.getTargetSeconds(),
                session.getActualSeconds(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getNote(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}
