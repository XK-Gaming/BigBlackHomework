package model.User;

/**
 * Enum vai trò user trong hệ thống.
 */
public enum UserRole {
    BIDDER,
    SELLER,
    ADMIN;
    /**
     * Precondition: text là tên vai trò dạng tiếng Việt từ UI/database.
     * Postcondition: Method trả về UserRole tương ứng, hoặc null nếu không khớp.
     */
    public static UserRole fromString(String text) {
        if(text.equals("Người bán")){
            return SELLER;
        }
        if (text.equals("Người đấu giá")){
            return BIDDER;
        }
        if (text.equals("Admin")){
            return ADMIN;
        }
        return null; // Hoặc trả về một Role mặc định
    }
}
