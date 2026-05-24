package model.Items;

import java.time.Instant;
import java.util.Map;

/**
 * Factory tạo subclass Item phù hợp với loại sản phẩm được chọn.
 */
public class ItemFactory {
    /**
     * Precondition: type là một trong các chuỗi loại item được UI hỗ trợ; extraFields chứa
     * các key cần cho từng loại; fileName là tên ảnh đã chọn.
     * Postcondition: Method trả về Electronics, Vehicle hoặc Art tương ứng với type.
     * NOTE: Ném IllegalArgumentException nếu type không hợp lệ.
     */
    public static Item createItem(String type, String name, String desc, double price,
                                  Instant start, Instant end, String owner,
                                  Map<String, String> extraFields, String fileName) {
        return createItem(type, name, desc, price, 0, start, end, owner, extraFields, fileName);
    }

    public static Item createItem(String type, String name, String desc, double price, double minBid,
                                  Instant start, Instant end, String owner,
                                  Map<String, String> extraFields, String fileName) {

        switch (type) {
            case "Điện tử":
                return new Electronics(name, desc, price, minBid, start, end, owner,
                        extraFields.get("brand"), extraFields.get("model"), fileName);

            case "Phương tiện giao thông":
                return new Vehicle(name, desc, price, minBid, start, end, owner,
                        extraFields.get("manufacturer"), extraFields.get("year"), fileName);

            case "Mỹ thuật":
                return new Art(name, desc, price, minBid, start, end, owner,
                        extraFields.get("artist"), fileName);

            default:
                throw new IllegalArgumentException("Loại mặt hàng không hợp lệ: " + type);
        }
    }
}
