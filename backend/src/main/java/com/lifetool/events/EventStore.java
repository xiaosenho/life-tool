package com.lifetool.events;

import java.util.List;
import java.util.Optional;

public interface EventStore {
    AnniversaryEvent save(AnniversaryEvent event);

    Optional<AnniversaryEvent> findById(String id);

    List<AnniversaryEvent> findByUserId(String userId);
}
