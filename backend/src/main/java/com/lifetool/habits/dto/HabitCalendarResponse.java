package com.lifetool.habits.dto;

import java.util.List;

public record HabitCalendarResponse(
        List<HabitResponse> habits,
        List<HabitCheckinResponse> checkins
) {
}
