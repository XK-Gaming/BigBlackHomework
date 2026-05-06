package model.Items;

import java.time.Instant;
import java.util.Map;

/**
 * Factory tạo {@link Item} theo loại sản phẩm được chọn từ UI.
 *
 * <p>Quy ước type (chuỗi tiếng Việt) đang bám theo combobox UI:
 * <ul>
 *   <li>"Điện tử" -> {@link Electronics}</li>
 *   <li>"Phương tiện giao thông" -> {@link Vehicle}</li>
 *   <li>"Mỹ thuật" -> {@link Art}</li>
 * </ul>
 */
public class ItemFactory {
    /**
     * Precondition:
     * - {@code type} thuộc một trong các giá trị đã hỗ trợ.
     * - {@code name} không rỗng; {@code price} hợp lệ; {@code start/end} không null.
     * - {@code extraFields} chứa đủ field phụ tương ứng theo type.
     * Postcondition: Tạo và trả về một {@link Item} thuộc subclass tương ứng, đã được gán đầy đủ thông tin.
     * NOTE:
     * - {@code fileName} thường là URL ảnh (Cloudinary) hoặc tên file resource.
     * - Nếu thiếu field trong {@code extraFields}, constructor có thể nhận null (tuỳ subclass xử lý).
     * Method returns: {@link Item}.
     * @throws IllegalArgumentException NOTE: Ném ra nếu {@code type} không hợp lệ.
     */
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