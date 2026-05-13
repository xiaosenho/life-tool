package com.lifetool.events.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lifetool.events.AnniversaryEvent;

public record EventResponse(
        String id,
        String type,
        String title,
        LocalDate eventDate,
        String repeatRule,
        List<Integer> remindDaysBefore,
        long daysUntil,
        LocalDate nextOccurrenceDate,
        String note,
        String mediaAssetId,
        Instant createdAt,
        Instant updatedAt) {

    public static EventResponse from(
            AnniversaryEvent event,
            long daysUntil,
            LocalDate nextOccurrenceDate) {
        return new EventResponse(
                event.getId(),
                event.getType(),
                event.getTitle(),
                event.getEventDate(),
                event.getRepeatRule(),
                event.getRemindDaysBefore(),
                daysUntil,
                nextOccurrenceDate,
                event.getNote(),
                event.getMediaAssetId(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
