package com.bbbc.domain.user;

public final class Seller extends User {
    public Seller(String username, String email, String passwordHash) {
        super(username, email, passwordHash, UserRole.SELLER);
    }

    @Override
    public String printInfo() {
        return "Seller{name='%s', email='%s'}".formatted(getUsername(), getEmail());
    }
}
