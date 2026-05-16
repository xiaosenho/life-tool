package com.lifetool.habits.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.lifetool.habits.HabitCheckin;

public record HabitCheckinResponse(
        String id,
        String userId,
        String habitId,
        LocalDate checkinDate,
        int count,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    public static HabitCheckinResponse from(HabitCheckin checkin) {
        return new HabitCheckinResponse(
                checkin.getId(),
                checkin.getUserId(),
                checkin.getHabitId(),
                checkin.getCheckinDate(),
                checkin.getCount(),
                checkin.getNote(),
                checkin.getCreatedAt(),
                checkin.getUpdatedAt());
    }
}
