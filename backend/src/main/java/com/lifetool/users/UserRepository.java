package com.lifetool.users;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final Map<String, User> byId = new ConcurrentHashMap<>();
    private final Map<String, User> byEmail = new ConcurrentHashMap<>();

    public Optional<User> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(byEmail.get(email.toLowerCase()));
    }

    public User save(User user) {
        byId.put(user.getId(), user);
        byEmail.put(user.getEmail().toLowerCase(), user);
        return user;
    }

    public boolean existsByEmail(String email) {
        return byEmail.containsKey(email.toLowerCase());
    }
}
