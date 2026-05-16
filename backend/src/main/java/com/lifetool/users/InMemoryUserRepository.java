package com.lifetool.users;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> byId = new ConcurrentHashMap<>();
    private final Map<String, User> byEmail = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Map<String, User> findByIds(Collection<String> ids) {
        Map<String, User> results = new LinkedHashMap<>();
        if (ids == null) {
            return results;
        }
        ids.forEach(id -> {
            User user = byId.get(id);
            if (user != null) {
                results.put(id, user);
            }
        });
        return results;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(byEmail.get(email.toLowerCase()));
    }

    @Override
    public User save(User user) {
        byId.put(user.getId(), user);
        byEmail.put(user.getEmail().toLowerCase(), user);
        return user;
    }

    @Override
    public boolean existsByEmail(String email) {
        return byEmail.containsKey(email.toLowerCase());
    }
}
