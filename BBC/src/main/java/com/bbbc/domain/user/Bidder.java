package com.bbbc.domain.user;

public final class Bidder extends User {
    public Bidder(String username, String email, String passwordHash) {
        super(username, email, passwordHash, UserRole.BIDDER);
    }

    @Override
    public String printInfo() {
        return "Bidder{name='%s', email='%s'}".formatted(getUsername(), getEmail());
    }
}
