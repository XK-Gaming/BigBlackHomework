package com.bbbc.client.model;

public final class ClientSession {
    private final String userId;
    private final String username;
    private final String role;
    private final String token;

    public ClientSession(String userId, String username, String role, String token) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getToken() {
        return token;
    }

    public boolean isSellerOrAdmin() {
        return "SELLER".equals(role) || "ADMIN".equals(role);
    }
}
