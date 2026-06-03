package model.User;

public enum UserRole {
    BIDDER,
    SELLER,
    ADMIN;
    // Đổi chuỗi sang enum.
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
        return null;
    }
}
