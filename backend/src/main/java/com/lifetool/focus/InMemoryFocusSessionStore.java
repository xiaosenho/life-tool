package com.lifetool.focus;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.lifetool.common.TimeSupport;

@Repository
@Profile("!postgres")
public class InMemoryFocusSessionStore implements FocusSessionStore {

    private final Map<String, FocusSession> byId = new ConcurrentHashMap<>();

    @Override
    public FocusSession save(FocusSession session) {
        byId.put(session.getId(), session);
        return session;
    }

    @Override
    public Optional<FocusSession> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<FocusSession> findByUserId(String userId) {
        return byId.values().stream()
                .filter(s -> userId.equals(s.getUserId()))
                .toList();
    }

    @Override
    public List<FocusSession> findByUserIdAndMonth(String userId, String month) {
        YearMonth ym = YearMonth.parse(month);
        return byId.values().stream()
                .filter(s -> userId.equals(s.getUserId()) && s.getStartedAt() != null)
                .filter(s -> TimeSupport.toBusinessMonth(s.getStartedAt()).equals(ym))
                .toList();
    }
}
