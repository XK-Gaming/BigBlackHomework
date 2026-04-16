package service.impl;

import domain.user.Admin;
import domain.user.Bidder;
import domain.user.Seller;
import domain.user.User;
import service.UserService;

import java.util.ArrayList;
import java.util.List;

public class UserServiceImpl implements UserService {
    private final List<User> users = new ArrayList<>();

    @Override
    public User registerUser(String role, String username, String password, String fullName) {
        String userId = generateUserId();
        String normalizedRole = normalizeRole(role);

        User user = switch (normalizedRole) {
            case "seller" -> new Seller(userId, fullName, username, password);
            case "bidder" -> new Bidder(userId, fullName, username, password);
            case "admin" -> new Admin(userId, fullName, username, password);
            default -> throw new IllegalArgumentException("Role khong hop le: " + role);
        };

        validateNewUser(user);
        users.add(user);
        return user;
    }

    @Override
    public User login(String username, String password) {
        for (User user : users) {
            if (user.getUsername().equals(username) && user.authenticate(password)) {
                return user;
            }
        }
        return null;
    }

    @Override
    public List<User> getUsers() {
        return new ArrayList<>(users);
    }

    private void validateNewUser(User newUser) {
        for (User user : users) {
            if (user.getId().equals(newUser.getId())) {
                throw new IllegalArgumentException("Id user da ton tai: " + newUser.getId());
            }
            if (user.getUsername().equalsIgnoreCase(newUser.getUsername())) {
                throw new IllegalArgumentException("Username da ton tai: " + newUser.getUsername());
            }
        }
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role khong duoc rong.");
        }
        return role.trim().toLowerCase();
    }

    private String generateUserId() {
        return "U" + (users.size() + 1);
    }
}
