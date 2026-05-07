package model.User;

/**
 * Role/phân quyền của user trong hệ thống.
 *
 * <p>NOTE: UI hiện dùng chuỗi tiếng Việt ("Người bán", "Người đấu giá") và map sang enum bằng {@link #fromString(String)}.
 */
public enum UserRole {
    BIDDER,
    SELLER,
    ADMIN;

    /**
     * Precondition: {@code text} khác null.
     * Postcondition: Không đổi state.
     * Method returns: {@link UserRole} tương ứng với chuỗi hiển thị; hoặc null nếu không map được.
     * NOTE: Trả về null có thể gây NullPointerException ở nơi khác; nếu muốn an toàn hơn có thể trả về default role.
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
