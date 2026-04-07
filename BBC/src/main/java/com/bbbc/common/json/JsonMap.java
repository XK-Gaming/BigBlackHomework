package com.bbbc.common.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JsonMap {
    private final Map<String, Object> values;

    public JsonMap(Map<String, Object> values) {
        this.values = values;
    }

    public String getString(String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public String requireString(String key) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            throw new JsonException("Missing string field: " + key);
        }
        return value;
    }

    public double getDouble(String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0d;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    public boolean getBoolean(String key) {
        Object value = values.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        Object value = values.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMapList(String key) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                results.add((Map<String, Object>) map);
            }
        }
        return results;
    }
}
