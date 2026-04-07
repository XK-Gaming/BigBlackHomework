package com.bbbc.server.repository;

import com.bbbc.domain.user.User;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryUserRepository implements UserRepository {
    private final ConcurrentHashMap<String, User> usersById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userIdsByUsername = new ConcurrentHashMap<>();

    @Override
    public User save(User user) {
        usersById.put(user.getId(), user);
        userIdsByUsername.put(user.getUsername(), user.getId());
        return user;
    }

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String userId = userIdsByUsername.get(username);
        if (userId == null) {
            return Optional.empty();
        }
        return findById(userId);
    }

    @Override
    public List<User> findAll() {
        return usersById.values().stream().toList();
    }
}
