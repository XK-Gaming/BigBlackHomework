package com.bbbc.common.message;

import com.bbbc.common.json.JsonMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ApiMessage {
    private final String messageType;
    private final String requestId;
    private final String action;
    private final boolean success;
    private final String message;
    private final String token;
    private final Map<String, Object> payload;

    public ApiMessage(
            String messageType,
            String requestId,
            String action,
            boolean success,
            String message,
            String token,
            Map<String, Object> payload
    ) {
        this.messageType = messageType;
        this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
        this.action = action;
        this.success = success;
        this.message = message == null ? "" : message;
        this.token = token;
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static ApiMessage request(String action, String token, Map<String, Object> payload) {
        return new ApiMessage("REQUEST", null, action, true, "", token, payload);
    }

    public static ApiMessage response(String requestId, String action, boolean success, String message, Map<String, Object> payload) {
        return new ApiMessage("RESPONSE", requestId, action, success, message, null, payload);
    }

    public static ApiMessage event(String action, Map<String, Object> payload) {
        return new ApiMessage("EVENT", null, action, true, "", null, payload);
    }

    public String getMessageType() {
        return messageType;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getAction() {
        return action;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getToken() {
        return token;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageType", messageType);
        result.put("requestId", requestId);
        result.put("action", action);
        result.put("success", success);
        result.put("message", message);
        result.put("token", token);
        result.put("payload", payload);
        return result;
    }

    public static ApiMessage fromMap(Map<String, Object> map) {
        JsonMap jsonMap = new JsonMap(map);
        return new ApiMessage(
                jsonMap.requireString("messageType"),
                jsonMap.requireString("requestId"),
                jsonMap.getString("action"),
                jsonMap.getBoolean("success"),
                jsonMap.getString("message"),
                jsonMap.getString("token"),
                jsonMap.getMap("payload")
        );
    }
}
