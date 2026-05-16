package com.lifetool.users;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(String id);

    Map<String, User> findByIds(Collection<String> ids);

    Optional<User> findByEmail(String email);

    User save(User user);

    boolean existsByEmail(String email);
}
