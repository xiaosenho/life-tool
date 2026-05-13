package com.lifetool.events;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class EventStore {

    private final Map<String, AnniversaryEvent> eventsById = new ConcurrentHashMap<>();

    public AnniversaryEvent save(AnniversaryEvent event) {
        eventsById.put(event.getId(), event);
        return event;
    }

    public Optional<AnniversaryEvent> findById(String id) {
        return Optional.ofNullable(eventsById.get(id));
    }

    public List<AnniversaryEvent> findByUserId(String userId) {
        return eventsById.values().stream()
                .filter(event -> event.getUserId().equals(userId))
                .filter(event -> !event.isDeleted())
                .toList();
    }
}
