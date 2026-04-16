package domain.factory;

import java.time.Instant;
import java.util.Map;

import domain.exception.ValidationException;
import domain.item.Art;
import domain.item.Electronics;
import domain.item.Item;
import domain.item.Vehicle;

public final class ItemFactory {
    public static Item create(
            ItemType itemType,
            String id,
            String name,
            String description,
            double startingPrice,
            Instant startTime,
            Instant endTime,
            String sellerId,
            Map<String, Object> attributes
    ) {
        if (itemType == null) {
            throw new ValidationException("Item type must not be null.");
        }

        Map<String, Object> safeAttributes = attributes == null ? Map.of() : attributes;

        return switch (itemType) {
            case ELECTRONICS -> new Electronics(
                    id,
                    name,
                    description,
                    startingPrice,
                    startTime,
                    endTime,
                    sellerId,
                    requireString(safeAttributes, "brand")
            );
            case ART -> new Art(
                    id,
                    name,
                    description,
                    startingPrice,
                    startTime,
                    endTime,
                    sellerId,
                    requireString(safeAttributes, "artist")
            );
            case VEHICLE -> new Vehicle(
                    id,
                    name,
                    description,
                    startingPrice,
                    startTime,
                    endTime,
                    sellerId,
                    requireString(safeAttributes, "manufacturer")
            );
        };
    }

    private static String requireString(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value == null) {
            throw new ValidationException("Missing attribute: " + key);
        }

        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new ValidationException("Attribute must not be blank: " + key);
        }
        return text;
    }
}
