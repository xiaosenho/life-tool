package com.lifetool.events;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.lifetool.common.TimeSupport;
import com.lifetool.push.PushNotificationCommand;
import com.lifetool.push.PushNotificationService;

@Component
public class EventReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventReminderScheduler.class);

    private final EventStore eventStore;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;
    private final Map<String, LocalDate> sentReminderMarkers = new ConcurrentHashMap<>();

    public EventReminderScheduler(EventStore eventStore, PushNotificationService pushNotificationService) {
        this.eventStore = eventStore;
        this.pushNotificationService = pushNotificationService;
        this.clock = TimeSupport.BUSINESS_CLOCK;
    }

    @Scheduled(fixedDelayString = "${lifetool.push.reminders.scan-delay-ms:3600000}")
    public void scanAndPushDueReminders() {
        LocalDate today = LocalDate.now(clock);
        int pushed = 0;
        for (AnniversaryEvent event : eventStore.findAllActive()) {
            ReminderMatch match = findReminderMatch(event, today);
            if (match == null) {
                continue;
            }
            String markerKey = buildMarkerKey(event, match.displayDate());
            LocalDate existing = sentReminderMarkers.putIfAbsent(markerKey, today);
            if (existing != null && existing.equals(today)) {
                continue;
            }
            pushNotificationService.pushToUser(new PushNotificationCommand(
                    buildTitle(event, match.offsetDays()),
                    buildBody(event, match.nextOccurrenceDate(), match.offsetDays()),
                    event.getUserId(),
                    "lifetool://records?tab=events",
                    Map.of(
                            "scene", "anniversary_reminder",
                            "eventId", event.getId(),
                            "eventType", event.getType(),
                            "displayDate", match.displayDate().toString(),
                            "nextOccurrenceDate", match.nextOccurrenceDate().toString(),
                            "reminderOffsetDays", match.offsetDays()
                    )
            ));
            pushed++;
        }
        if (pushed > 0) {
            log.info("Anniversary reminder push completed, pushed={}", pushed);
        }
        cleanupMarkers(today.minusDays(2));
    }

    private ReminderMatch findReminderMatch(AnniversaryEvent event, LocalDate today) {
        LocalDate nextOccurrence = nextOccurrence(event, today);
        Set<Integer> offsets = new HashSet<>(event.getRemindDaysBefore());
        offsets.add(0);
        return offsets.stream()
                .filter(offset -> offset != null && offset >= 0)
                .sorted()
                .map(offset -> new ReminderMatch(nextOccurrence.minusDays(offset), nextOccurrence, offset))
                .filter(match -> match.displayDate().equals(today))
                .findFirst()
                .orElse(null);
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
        LocalDate candidate = EventDateSupport.safeDate(referenceDate.getYear(), date.getMonthValue(), date.getDayOfMonth());
        if (candidate.isBefore(referenceDate)) {
            candidate = EventDateSupport.safeDate(referenceDate.getYear() + 1, date.getMonthValue(), date.getDayOfMonth());
        }
        return candidate;
    }

    private LocalDate nextMonthly(LocalDate date, LocalDate referenceDate) {
        return EventDateSupport.nextMonthly(date, referenceDate);
    }

    private LocalDate nextWeekly(LocalDate date, LocalDate referenceDate) {
        LocalDate candidate = date;
        while (candidate.isBefore(referenceDate)) {
            long weeks = Math.max(1, ChronoUnit.WEEKS.between(candidate, referenceDate));
            candidate = candidate.plusWeeks(weeks);
        }
        return candidate;
    }

    private String buildMarkerKey(AnniversaryEvent event, LocalDate displayDate) {
        return event.getId() + "@" + displayDate;
    }

    private void cleanupMarkers(LocalDate keepAfter) {
        sentReminderMarkers.entrySet().removeIf(entry -> entry.getValue().isBefore(keepAfter));
    }

    private String buildTitle(AnniversaryEvent event, int offsetDays) {
        if (offsetDays <= 0) {
            return switch (event.getType()) {
                case "birthday" -> "今天是一个生日提醒";
                case "todo_reminder" -> "今天有一条提醒";
                default -> "今天有一个重要日子";
            };
        }
        return switch (event.getType()) {
            case "birthday" -> "生日提醒快到了";
            case "todo_reminder" -> "提醒事项快到了";
            default -> "纪念日快到了";
        };
    }

    private String buildBody(AnniversaryEvent event, LocalDate nextOccurrenceDate, int offsetDays) {
        if (offsetDays <= 0) {
            return event.getTitle() + " 就在今天";
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), nextOccurrenceDate);
        long safeDays = Math.max(days, 0);
        return event.getTitle() + " 将在 " + safeDays + " 天后到来";
    }

    private record ReminderMatch(LocalDate displayDate, LocalDate nextOccurrenceDate, int offsetDays) {
    }

    private static final class EventDateSupport {
        private EventDateSupport() {
        }

        static LocalDate safeDate(int year, int month, int day) {
            java.time.YearMonth ym = java.time.YearMonth.of(year, month);
            return LocalDate.of(year, month, Math.min(day, ym.lengthOfMonth()));
        }

        static LocalDate nextMonthly(LocalDate date, LocalDate referenceDate) {
            java.time.YearMonth month = java.time.YearMonth.from(referenceDate);
            LocalDate candidate = safeDate(month.getYear(), month.getMonthValue(), date.getDayOfMonth());
            if (candidate.isBefore(referenceDate)) {
                java.time.YearMonth nextMonth = month.plusMonths(1);
                candidate = safeDate(nextMonth.getYear(), nextMonth.getMonthValue(), date.getDayOfMonth());
            }
            return candidate;
        }
    }
}
