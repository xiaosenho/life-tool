package com.lifetool.habits.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateHabitRequest(
        @NotBlank String name,
        String description,
        String frequencyType,
        int[] frequencyDays,
        int targetCount,
        String color,
        String icon) {
}
