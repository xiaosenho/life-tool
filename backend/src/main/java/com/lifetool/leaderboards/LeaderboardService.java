package com.lifetool.leaderboards;

import com.lifetool.leaderboards.dto.LeaderboardEntryDto;
import com.lifetool.leaderboards.dto.LeaderboardDetailResponse;
import com.lifetool.leaderboards.dto.LeaderboardResponse;
import com.lifetool.friends.FriendStore;
import com.lifetool.users.User;
import com.lifetool.users.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LeaderboardService {

    private final LeaderboardStatsStore statsStore;
    private final FriendStore friendStore;
    private final UserRepository userRepository;

    public LeaderboardService(LeaderboardStatsStore statsStore, FriendStore friendStore, UserRepository userRepository) {
        this.statsStore = statsStore;
        this.friendStore = friendStore;
        this.userRepository = userRepository;
    }

    public LeaderboardResponse getFocusToday(String userId) {
        return toSummary(buildDetail(userId, "today", "focus_seconds"));
    }

    public LeaderboardResponse getFocusWeek(String userId) {
        return toSummary(buildDetail(userId, "week", "focus_seconds:week"));
    }

    public LeaderboardResponse getHabitsToday(String userId) {
        return toSummary(buildDetail(userId, "today", "habit_completion"));
    }

    public LeaderboardResponse getStreaks(String userId) {
        return toSummary(buildDetail(userId, "all_time", "streak_days"));
    }

    public LeaderboardDetailResponse getFocusTodayDetail(String userId) {
        return buildDetail(userId, "today", "focus_seconds");
    }

    public LeaderboardDetailResponse getFocusWeekDetail(String userId) {
        return buildDetail(userId, "week", "focus_seconds:week");
    }

    public LeaderboardDetailResponse getHabitsTodayDetail(String userId) {
        return buildDetail(userId, "today", "habit_completion");
    }

    public LeaderboardDetailResponse getStreaksDetail(String userId) {
        return buildDetail(userId, "all_time", "streak_days");
    }

    private LeaderboardEntryDto buildEntry(String userId, User user, long value, int rank) {
        String displayName = user != null ? user.getDisplayName() : "Unknown";
        return new LeaderboardEntryDto(userId, displayName, null, value, rank);
    }

    private LeaderboardResponse toSummary(LeaderboardDetailResponse detail) {
        return new LeaderboardResponse(detail.period(), normalizeMetric(detail.metric()), detail.entries());
    }

    private LeaderboardDetailResponse buildDetail(String userId, String period, String metric) {
        List<String> participantIds = collectParticipantIds(userId);
        Map<String, User> usersById = userRepository.findByIds(participantIds);
        List<UserMetric> metrics = participantIds.stream()
                .map(id -> new UserMetric(id, metricValue(metric, id)))
                .sorted(Comparator.comparingLong(UserMetric::value).reversed().thenComparing(UserMetric::userId))
                .toList();

        List<LeaderboardEntryDto> entries = new ArrayList<>();
        long previousValue = -1;
        int rank = 0;
        for (int index = 0; index < metrics.size(); index++) {
            UserMetric item = metrics.get(index);
            if (item.value() != previousValue) {
                rank = index + 1;
                previousValue = item.value();
            }
            entries.add(buildEntry(item.userId(), usersById.get(item.userId()), item.value(), rank));
        }

        LeaderboardEntryDto self = entries.stream()
                .filter(entry -> entry.userId().equals(userId))
                .findFirst()
                .orElseGet(() -> buildEntry(userId, usersById.get(userId), 0, 1));
        long gapToPrevious = computeGap(entries, self);
        return new LeaderboardDetailResponse(period, metric, entries, self, gapToPrevious, entries.size());
    }

    private String normalizeMetric(String metric) {
        if ("focus_seconds:week".equals(metric)) {
            return "focus_seconds";
        }
        return metric;
    }

    private List<String> collectParticipantIds(String userId) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(userId);
        friendStore.findFriendships(userId).forEach(friendship -> {
            ids.add(friendship.getUserId());
            ids.add(friendship.getFriendUserId());
        });
        return ids.stream().toList();
    }

    private long metricValue(String metric, String userId) {
        return switch (metric) {
            case "focus_seconds" -> statsStore.getFocusTodaySeconds(userId);
            case "focus_seconds:week" -> statsStore.getFocusWeekSeconds(userId);
            case "habit_completion" -> {
                long total = statsStore.getHabitsTodayTotal(userId);
                long completed = statsStore.getHabitsTodayCompletion(userId);
                yield total <= 0 ? completed : Math.round((completed * 100.0) / total);
            }
            case "streak_days" -> statsStore.getStreaksDays(userId);
            default -> 0L;
        };
    }

    private long computeGap(List<LeaderboardEntryDto> entries, LeaderboardEntryDto self) {
        int selfIndex = entries.indexOf(self);
        if (selfIndex <= 0) {
            return 0L;
        }
        return entries.get(selfIndex - 1).value() - self.value();
    }

    private record UserMetric(String userId, long value) {
    }
}
