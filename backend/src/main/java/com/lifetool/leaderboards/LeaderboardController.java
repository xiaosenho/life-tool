package com.lifetool.leaderboards;

import com.lifetool.common.ApiResponse;
import com.lifetool.leaderboards.dto.LeaderboardResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/focus")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> getFocus(
            @AuthenticationPrincipal String userId,
            @RequestParam String period) {
        LeaderboardResponse resp = switch (period) {
            case "today" -> leaderboardService.getFocusToday(userId);
            case "week" -> leaderboardService.getFocusWeek(userId);
            default -> throw new LeaderboardException("VALIDATION_ERROR",
                    "Invalid period: " + period + ". Allowed: today, week");
        };
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @GetMapping("/habits")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> getHabits(
            @AuthenticationPrincipal String userId,
            @RequestParam String period) {
        if (!"today".equals(period)) {
            throw new LeaderboardException("VALIDATION_ERROR",
                    "Invalid period: " + period + ". Allowed: today");
        }
        LeaderboardResponse resp = leaderboardService.getHabitsToday(userId);
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @GetMapping("/streaks")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> getStreaks(
            @AuthenticationPrincipal String userId) {
        LeaderboardResponse resp = leaderboardService.getStreaks(userId);
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }
}
