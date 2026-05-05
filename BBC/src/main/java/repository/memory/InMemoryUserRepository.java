package repository.memory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import model.User.Admin;
import model.User.Bidder;
import model.User.Seller;
import model.User.User;
import repository.UserRepository;

public final class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> usersByUsername = new LinkedHashMap<>();

    public InMemoryUserRepository() {
        seedDefaults();
    }

    @Override
    public synchronized User save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null.");
        }
        usersByUsername.put(normalize(user.getUsername()), user);
        return user;
    }

    @Override
    public synchronized Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersByUsername.get(normalize(username)));
    }

    @Override
    public synchronized boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public synchronized Collection<User> findAll() {
        return List.copyOf(usersByUsername.values());
    }

    private void seedDefaults() {
        save(new Seller("seller-1", "Demo Seller", "seller", "seller123"));
        save(new Bidder("bidder-1", "Demo Bidder", "bidder", "bidder123"));
        save(new Admin("admin-1", "Demo Admin", "admin", "admin123"));
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
