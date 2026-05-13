package com.lifetool.events.dto;

import java.time.LocalDate;
import java.util.List;

public record UpdateEventRequest(
        String type,
        String title,
        LocalDate eventDate,
        String repeatRule,
        List<Integer> remindDaysBefore,
        String note,
        String mediaAssetId) {
}
