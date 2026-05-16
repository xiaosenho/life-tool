package com.lifetool.events;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryEventStore implements EventStore {

    private final Map<String, AnniversaryEvent> eventsById = new ConcurrentHashMap<>();

    @Override
    public AnniversaryEvent save(AnniversaryEvent event) {
        eventsById.put(event.getId(), event);
        return event;
    }

    @Override
    public Optional<AnniversaryEvent> findById(String id) {
        return Optional.ofNullable(eventsById.get(id));
    }

    @Override
    public List<AnniversaryEvent> findByUserId(String userId) {
        return eventsById.values().stream()
                .filter(event -> event.getUserId().equals(userId))
                .filter(event -> !event.isDeleted())
                .toList();
    }
}
