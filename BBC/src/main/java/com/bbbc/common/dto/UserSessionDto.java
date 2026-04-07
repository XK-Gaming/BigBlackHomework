package com.bbbc.common.dto;

import com.bbbc.domain.user.User;

import java.util.LinkedHashMap;
import java.util.Map;

public record UserSessionDto(
        String userId,
        String username,
        String role,
        String token
) {
    public static UserSessionDto from(User user, String token) {
        return new UserSessionDto(user.getId(), user.getUsername(), user.getRole().name(), token);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", userId);
        map.put("username", username);
        map.put("role", role);
        map.put("token", token);
        return map;
    }
}
