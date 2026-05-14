package com.lifetool.habits.dto;

public record UpdateHabitRequest(
        String name,
        String description,
        String frequencyType,
        int[] frequencyDays,
        Integer targetCount,
        String color,
        String icon,
        Boolean archived) {
}
