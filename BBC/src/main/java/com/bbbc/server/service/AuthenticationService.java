package com.bbbc.server.service;

import com.bbbc.domain.exception.EntityNotFoundException;
import com.bbbc.domain.exception.UnauthorizedException;
import com.bbbc.domain.exception.ValidationException;
import com.bbbc.domain.user.Admin;
import com.bbbc.domain.user.Bidder;
import com.bbbc.domain.user.Seller;
import com.bbbc.domain.user.User;
import com.bbbc.domain.user.UserRole;
import com.bbbc.server.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthenticationService {
    private final UserRepository userRepository;
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String email, String password, UserRole role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ValidationException("Username and password are required");
        }
        userRepository.findByUsername(username).ifPresent(existing -> {
            throw new ValidationException("Username already exists");
        });
        User user = switch (role) {
            case BIDDER -> new Bidder(username, email, hash(password));
            case SELLER -> new Seller(username, email, hash(password));
            case ADMIN -> new Admin(username, email, hash(password));
        };
        return userRepository.save(user);
    }

    public String login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
        if (!user.getPasswordHash().equals(hash(password))) {
            throw new UnauthorizedException("Invalid username or password");
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, user.getId());
        return token;
    }

    public User requireUserByToken(String token) {
        String userId = sessions.get(token);
        if (userId == null) {
            throw new UnauthorizedException("Invalid session token");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    public User requireUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
