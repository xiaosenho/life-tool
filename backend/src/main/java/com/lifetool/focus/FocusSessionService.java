package com.lifetool.focus;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lifetool.focus.dto.CreateFocusSessionRequest;
import com.lifetool.focus.dto.EndFocusSessionRequest;
import com.lifetool.focus.dto.FocusSessionResponse;
import com.lifetool.leaderboards.LeaderboardStatsStore;

@Service
public class FocusSessionService {

    private static final List<String> VALID_MODES = List.of("pomodoro", "countdown", "stopwatch");
    private static final List<String> VALID_STATUSES = List.of("completed", "interrupted", "abandoned");

    private final FocusSessionStore store;
    private final LeaderboardStatsStore statsStore;

    public FocusSessionService(FocusSessionStore store, LeaderboardStatsStore statsStore) {
        this.store = store;
        this.statsStore = statsStore;
    }

    public FocusSessionResponse startSession(String userId, CreateFocusSessionRequest request) {
        String mode = request.mode() == null || request.mode().isBlank() ? "pomodoro" : request.mode();
        if (!VALID_MODES.contains(mode)) {
            throw new FocusException("VALIDATION_ERROR", "Invalid mode: " + mode);
        }
        int targetMinutes = request.targetMinutes();
        if (targetMinutes < 1 || targetMinutes > 180) {
            throw new FocusException("VALIDATION_ERROR", "targetMinutes must be between 1 and 180");
        }

        FocusSession session = new FocusSession();
        session.setUserId(userId);
        session.setMode(mode);
        session.setTargetSeconds(targetMinutes * 60);
        session.setActualSeconds(0);
        session.setStatus("running");
        session.setStartedAt(Instant.now());
        session.setNote(blankToNull(request.note()));

        store.save(session);
        return FocusSessionResponse.from(session);
    }

    public FocusSessionResponse endSession(String userId, String id, EndFocusSessionRequest request) {
        FocusSession session = findOwnedSession(userId, id);

        if (request.status() != null && !request.status().isBlank()) {
            if (!VALID_STATUSES.contains(request.status())) {
                throw new FocusException("VALIDATION_ERROR", "Invalid status: " + request.status());
            }
            session.setStatus(request.status());
        }
        session.setActualSeconds(Math.max(0, request.actualMinutes()) * 60);
        session.setEndedAt(Instant.now());
        if (request.note() != null) {
            session.setNote(blankToNull(request.note()));
        }
        session.setUpdatedAt(Instant.now());

        store.save(session);
        statsStore.refreshFocusStats(userId);
        return FocusSessionResponse.from(session);
    }

    public List<FocusSessionResponse> listSessions(String userId, String month) {
        if (month == null || month.isBlank()) {
            return store.findByUserId(userId).stream()
                    .map(FocusSessionResponse::from)
                    .toList();
        }
        return store.findByUserIdAndMonth(userId, month).stream()
                .map(FocusSessionResponse::from)
                .toList();
    }

    public FocusSessionResponse getSession(String userId, String id) {
        FocusSession session = findOwnedSession(userId, id);
        return FocusSessionResponse.from(session);
    }

    private FocusSession findOwnedSession(String userId, String id) {
        FocusSession session = store.findById(id)
                .orElseThrow(() -> new FocusException("NOT_FOUND", "Focus session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new FocusException("FORBIDDEN", "Access denied");
        }
        return session;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
