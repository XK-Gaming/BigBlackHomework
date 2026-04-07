package com.bbbc.domain.factory;

import com.bbbc.domain.exception.ValidationException;
import com.bbbc.domain.item.Art;
import com.bbbc.domain.item.Electronics;
import com.bbbc.domain.item.Item;
import com.bbbc.domain.item.ItemType;
import com.bbbc.domain.item.Vehicle;

import java.time.Instant;
import java.util.Map;

public final class ItemFactory {
    public Item create(
            ItemType itemType,
            String name,
            String description,
            double startingPrice,
            Instant startTime,
            Instant endTime,
            String sellerId,
            Map<String, Object> attributes
    ) {
        return switch (itemType) {
            case ELECTRONICS -> new Electronics(
                    name,
                    description,
                    startingPrice,
                    startTime,
                    endTime,
                    sellerId,
                    requireString(attributes, "brand"),
                    requireString(attributes, "model")
            );
            case ART -> new Art(
                    name,
                    description,
                    startingPrice,
                    startTime,
                    endTime,
                    sellerId,
                    requireString(attributes, "artist")
            );
            case VEHICLE -> new Vehicle(
                    name,
                    description,
                    startingPrice,
                    startTime,
                    endTime,
                    sellerId,
                    requireString(attributes, "manufacturer"),
                    ((Number) attributes.getOrDefault("year", 0)).intValue()
            );
        };
    }

    private String requireString(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value == null) {
            throw new ValidationException("Missing attribute: " + key);
        }
        return String.valueOf(value);
    }
}
