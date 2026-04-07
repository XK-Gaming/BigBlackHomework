package com.bbbc.domain.user;

public final class Admin extends User {
    public Admin(String username, String email, String passwordHash) {
        super(username, email, passwordHash, UserRole.ADMIN);
    }

    @Override
    public String printInfo() {
        return "Admin{name='%s', email='%s'}".formatted(getUsername(), getEmail());
    }
}
