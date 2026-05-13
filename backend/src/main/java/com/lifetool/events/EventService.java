package com.lifetool.events;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lifetool.events.dto.CreateEventRequest;
import com.lifetool.events.dto.EventResponse;
import com.lifetool.events.dto.UpdateEventRequest;

@Service
public class EventService {

    private static final List<String> TYPES = List.of(
            "anniversary", "birthday", "important_day", "todo_reminder");
    private static final List<String> REPEAT_RULES = List.of("none", "yearly", "monthly", "weekly");

    private final EventStore store;
    private final Clock clock;

    public EventService(EventStore store) {
        this.store = store;
        this.clock = Clock.systemUTC();
    }

    public List<EventResponse> listEvents(String userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new EventException("VALIDATION_ERROR", "from must be before or equal to to");
        }

        return store.findByUserId(userId).stream()
                .map(event -> responseForRange(event, from))
                .filter(response -> !response.nextOccurrenceDate().isBefore(from))
                .filter(response -> !response.nextOccurrenceDate().isAfter(to))
                .sorted(Comparator.comparing(EventResponse::nextOccurrenceDate))
                .toList();
    }

    public EventResponse createEvent(String userId, CreateEventRequest request) {
        String repeatRule = request.repeatRule() == null ? "none" : request.repeatRule();
        validateType(request.type());
        validateRepeatRule(repeatRule);
        validateReminders(request.remindDaysBefore());

        AnniversaryEvent event = new AnniversaryEvent();
        event.setUserId(userId);
        event.setType(request.type());
        event.setTitle(request.title().trim());
        event.setEventDate(request.eventDate());
        event.setRepeatRule(repeatRule);
        event.setRemindDaysBefore(normalizeReminders(request.remindDaysBefore()));
        event.setNote(blankToNull(request.note()));
        event.setMediaAssetId(blankToNull(request.mediaAssetId()));

        store.save(event);
        return responseForToday(event);
    }

    public EventResponse updateEvent(String userId, String id, UpdateEventRequest request) {
        AnniversaryEvent event = findOwnedEvent(userId, id);

        if (request.type() != null) {
            validateType(request.type());
            event.setType(request.type());
        }
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new EventException("VALIDATION_ERROR", "title is required");
            }
            event.setTitle(request.title().trim());
        }
        if (request.eventDate() != null) {
            event.setEventDate(request.eventDate());
        }
        if (request.repeatRule() != null) {
            validateRepeatRule(request.repeatRule());
            event.setRepeatRule(request.repeatRule());
        }
        if (request.remindDaysBefore() != null) {
            validateReminders(request.remindDaysBefore());
            event.setRemindDaysBefore(normalizeReminders(request.remindDaysBefore()));
        }
        if (request.note() != null) {
            event.setNote(blankToNull(request.note()));
        }
        if (request.mediaAssetId() != null) {
            event.setMediaAssetId(blankToNull(request.mediaAssetId()));
        }

        event.setUpdatedAt(Instant.now(clock));
        store.save(event);
        return responseForToday(event);
    }

    public void deleteEvent(String userId, String id) {
        AnniversaryEvent event = findOwnedEvent(userId, id);
        event.setDeleted(true);
        event.setUpdatedAt(Instant.now(clock));
        store.save(event);
    }

    public List<EventResponse> upcomingEvents(String userId, int days) {
        if (days < 1 || days > 366) {
            throw new EventException("VALIDATION_ERROR", "days must be between 1 and 366");
        }
        LocalDate today = today();
        LocalDate end = today.plusDays(days);
        return listEvents(userId, today, end);
    }

    private EventResponse responseForToday(AnniversaryEvent event) {
        return responseForRange(event, today());
    }

    private EventResponse responseForRange(AnniversaryEvent event, LocalDate referenceDate) {
        LocalDate next = nextOccurrence(event, referenceDate);
        long daysUntil = ChronoUnit.DAYS.between(today(), next);
        return EventResponse.from(event, daysUntil, next);
    }

    private LocalDate nextOccurrence(AnniversaryEvent event, LocalDate referenceDate) {
        LocalDate date = event.getEventDate();
        return switch (event.getRepeatRule()) {
            case "yearly" -> nextYearly(date, referenceDate);
            case "monthly" -> nextMonthly(date, referenceDate);
            case "weekly" -> nextWeekly(date, referenceDate);
            default -> date;
        };
    }

    private LocalDate nextYearly(LocalDate date, LocalDate referenceDate) {
        LocalDate candidate = safeDate(referenceDate.getYear(), date.getMonthValue(), date.getDayOfMonth());
        if (candidate.isBefore(referenceDate)) {
            candidate = safeDate(referenceDate.getYear() + 1, date.getMonthValue(), date.getDayOfMonth());
        }
        return candidate;
    }

    private LocalDate nextMonthly(LocalDate date, LocalDate referenceDate) {
        YearMonth month = YearMonth.from(referenceDate);
        LocalDate candidate = safeDate(month.getYear(), month.getMonthValue(), date.getDayOfMonth());
        if (candidate.isBefore(referenceDate)) {
            YearMonth nextMonth = month.plusMonths(1);
            candidate = safeDate(nextMonth.getYear(), nextMonth.getMonthValue(), date.getDayOfMonth());
        }
        return candidate;
    }

    private LocalDate nextWeekly(LocalDate date, LocalDate referenceDate) {
        LocalDate candidate = date;
        while (candidate.isBefore(referenceDate)) {
            long weeks = Math.max(1, ChronoUnit.WEEKS.between(candidate, referenceDate));
            candidate = candidate.plusWeeks(weeks);
        }
        return candidate;
    }

    private LocalDate safeDate(int year, int month, int day) {
        YearMonth ym = YearMonth.of(year, month);
        return LocalDate.of(year, month, Math.min(day, ym.lengthOfMonth()));
    }

    private AnniversaryEvent findOwnedEvent(String userId, String id) {
        AnniversaryEvent event = store.findById(id)
                .orElseThrow(() -> new EventException("NOT_FOUND", "Event not found"));
        if (event.isDeleted()) {
            throw new EventException("NOT_FOUND", "Event not found");
        }
        if (!event.getUserId().equals(userId)) {
            throw new EventException("FORBIDDEN", "Access denied");
        }
        return event;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static void validateType(String type) {
        if (!TYPES.contains(type)) {
            throw new EventException("VALIDATION_ERROR", "type is invalid");
        }
    }

    private static void validateRepeatRule(String repeatRule) {
        if (!REPEAT_RULES.contains(repeatRule)) {
            throw new EventException("VALIDATION_ERROR", "repeatRule is invalid");
        }
    }

    private static void validateReminders(List<Integer> reminders) {
        if (reminders == null) return;
        boolean invalid = reminders.stream().anyMatch(day -> day == null || day < 0 || day > 366);
        if (invalid) {
            throw new EventException("VALIDATION_ERROR", "remindDaysBefore must be between 0 and 366");
        }
    }

    private static List<Integer> normalizeReminders(List<Integer> reminders) {
        if (reminders == null) return List.of();
        return reminders.stream().distinct().sorted(Comparator.reverseOrder()).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
