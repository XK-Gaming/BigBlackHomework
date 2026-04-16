package service;

import domain.user.User;

import java.util.List;

public interface UserService {
    User registerUser(String role, String username, String password, String fullName);

    User login(String username, String password);

    List<User> getUsers();
}
