package com.lifetool.users;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(String id);

    Optional<User> findByEmail(String email);

    User save(User user);

    boolean existsByEmail(String email);
}
