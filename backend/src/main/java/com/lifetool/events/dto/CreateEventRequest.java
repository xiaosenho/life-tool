package com.lifetool.events.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEventRequest(
        @NotBlank String type,
        @NotBlank String title,
        @NotNull LocalDate eventDate,
        String repeatRule,
        List<Integer> remindDaysBefore,
        String note,
        String mediaAssetId) {
}
