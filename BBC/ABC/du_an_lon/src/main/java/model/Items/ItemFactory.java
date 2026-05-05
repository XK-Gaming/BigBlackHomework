package model.Items;

import java.time.Instant;
import java.util.Map;

public class ItemFactory {
    public static Item createItem(String type, String name, String desc, double price,
                                  Instant start, Instant end, String owner,
                                  Map<String, String> extraFields, String fileName) {

        switch (type) {
            case "Điện tử":
                return new Electronics(name, desc, price, start, end, owner,
                        extraFields.get("brand"), extraFields.get("model"), fileName);

            case "Phương tiện giao thông":
                return new Vehicle(name, desc, price, start, end, owner,
                        extraFields.get("manufacturer"), extraFields.get("year"), fileName);

            case "Mỹ thuật":
                return new Art(name, desc, price, start, end, owner,
                        extraFields.get("artist"), fileName);

            default:
                throw new IllegalArgumentException("Loại mặt hàng không hợp lệ: " + type);
        }
    }
}