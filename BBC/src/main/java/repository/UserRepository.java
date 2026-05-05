package repository;

import java.util.Collection;
import java.util.Optional;

import model.User.User;

public interface UserRepository {
    User save(User user);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Collection<User> findAll();
}
