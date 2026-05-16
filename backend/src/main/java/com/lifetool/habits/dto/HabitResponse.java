package com.lifetool.habits.dto;

import java.time.Instant;

import com.lifetool.habits.Habit;

public record HabitResponse(
        String id,
        String userId,
        String name,
        String description,
        String frequencyType,
        int[] frequencyDays,
        int targetCount,
        String color,
        String icon,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public static HabitResponse from(Habit habit) {
        return new HabitResponse(
                habit.getId(),
                habit.getUserId(),
                habit.getName(),
                habit.getDescription(),
                habit.getFrequencyType(),
                habit.getFrequencyDays(),
                habit.getTargetCount(),
                habit.getColor(),
                habit.getIcon(),
                habit.isArchived(),
                habit.getCreatedAt(),
                habit.getUpdatedAt());
    }
}
