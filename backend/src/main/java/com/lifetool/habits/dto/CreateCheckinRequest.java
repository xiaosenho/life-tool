package com.lifetool.habits.dto;

import java.time.LocalDate;

public record CreateCheckinRequest(
        int count,
        String note,
        LocalDate checkinDate) {
}
