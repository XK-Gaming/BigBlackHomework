package model.Items;


/**
 * Enum loại sản phẩm được hỗ trợ trong hệ thống đấu giá.
 */
public enum ItemType {
    ELECTRONICS,
    ART,
    VEHICLE;
    /**
     * Precondition: text là tên loại item dạng tiếng Việt từ UI hoặc database.
     * Postcondition: Method trả về ItemType tương ứng, hoặc null nếu không khớp loại nào.
     */
    public static ItemType fromString(String text) {
        if(text.equals("Mỹ thuật")){
            return ART;
        }
        if (text.equals("Điện tử")){
            return ELECTRONICS;
        }
        if (text.equals("Phương tiện giao thông")){
            return VEHICLE;
        }
        return null; // Hoặc trả về một Role mặc định
    }
}
