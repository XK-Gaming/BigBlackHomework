package service;

import java.util.List;
import java.util.UUID;

import model.User.Admin;
import model.User.Bidder;
import model.User.Seller;
import model.User.User;
import model.exception.AuthenticationException;
import model.exception.ValidationException;
import repository.UserRepository;
import service.dto.RegistrationRequest;

public final class AuthService {
    public static final String SELLER_ROLE_LABEL = "Nguoi ban";
    public static final String BIDDER_ROLE_LABEL = "Nguoi dau gia";

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("UserRepository must not be null.");
        }
        this.userRepository = userRepository;
    }

    public List<String> getSupportedRoleLabels() {
        return List.of(SELLER_ROLE_LABEL, BIDDER_ROLE_LABEL);
    }

    public User login(String username, String password) {
        String safeUsername = requireText(username, "Username is required.");
        String safePassword = requireText(password, "Password is required.");

        User user = userRepository.findByUsername(safeUsername)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));
        if (!user.authenticate(safePassword)) {
            throw new AuthenticationException("Invalid username or password.");
        }
        return user;
    }

    public User register(RegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request must not be null.");
        }

        String fullName = requireText(request.fullName(), "Full name is required.");
        String username = requireText(request.username(), "Username is required.");
        String password = requireText(request.password(), "Password is required.");
        String confirmPassword = requireText(request.confirmPassword(), "Confirm password is required.");
        String email = requireText(request.email(), "Email is required.");
        if (!email.contains("@")) {
            throw new ValidationException("Email is not valid.");
        }
        if (!password.equals(confirmPassword)) {
            throw new ValidationException("Passwords do not match.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new AuthenticationException("Username already exists.");
        }

        User user = switch (resolveRole(request.roleLabel())) {
            case "SELLER" -> new Seller(nextId("seller"), fullName, username, password);
            case "BIDDER" -> new Bidder(nextId("bidder"), fullName, username, password);
            case "ADMIN" -> new Admin(nextId("admin"), fullName, username, password);
            default -> throw new ValidationException("Unsupported role.");
        };

        return userRepository.save(user);
    }

    private String resolveRole(String roleLabel) {
        String safeRole = requireText(roleLabel, "Role is required.");
        if (SELLER_ROLE_LABEL.equals(safeRole)) {
            return "SELLER";
        }
        if (BIDDER_ROLE_LABEL.equals(safeRole)) {
            return "BIDDER";
        }
        if ("Admin".equalsIgnoreCase(safeRole)) {
            return "ADMIN";
        }
        throw new ValidationException("Unsupported role.");
    }

    private String nextId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }
}
