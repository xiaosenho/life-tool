package com.lifetool.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lifetool.push.PushNotificationCommand;
import com.lifetool.push.PushNotificationService;

class EventReminderSchedulerTest {

    @Test
    void pushesReminderWhenEventIsDueToday() {
        InMemoryEventStore store = new InMemoryEventStore();
        CapturingPushNotificationService pushService = new CapturingPushNotificationService();
        EventReminderScheduler scheduler = new EventReminderScheduler(store, pushService);

        AnniversaryEvent event = new AnniversaryEvent();
        event.setUserId("user-1");
        event.setType("anniversary");
        event.setTitle("测试纪念日");
        event.setEventDate(LocalDate.now());
        event.setRepeatRule("none");
        event.setRemindDaysBefore(List.of(1, 7));
        store.save(event);

        scheduler.scanAndPushDueReminders();

        assertThat(pushService.commands).hasSize(1);
        PushNotificationCommand command = pushService.commands.getFirst();
        assertThat(command.targetUserId()).isEqualTo("user-1");
        assertThat(command.deepLink()).isEqualTo("lifetool://records?tab=events");
        assertThat(command.extras()).containsEntry("scene", "anniversary_reminder");
    }

    @Test
    void doesNotPushDuplicateReminderWithinSameDay() {
        InMemoryEventStore store = new InMemoryEventStore();
        CapturingPushNotificationService pushService = new CapturingPushNotificationService();
        EventReminderScheduler scheduler = new EventReminderScheduler(store, pushService);

        AnniversaryEvent event = new AnniversaryEvent();
        event.setUserId("user-2");
        event.setType("birthday");
        event.setTitle("生日");
        event.setEventDate(LocalDate.now().plusDays(1));
        event.setRepeatRule("none");
        event.setRemindDaysBefore(List.of(1));
        store.save(event);

        scheduler.scanAndPushDueReminders();
        scheduler.scanAndPushDueReminders();

        assertThat(pushService.commands).hasSize(1);
    }

    private static final class CapturingPushNotificationService implements PushNotificationService {
        private final List<PushNotificationCommand> commands = new ArrayList<>();

        @Override
        public void pushToUser(PushNotificationCommand command) {
            commands.add(command);
        }
    }
}
