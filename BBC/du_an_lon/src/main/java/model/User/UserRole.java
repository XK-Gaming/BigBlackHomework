package model.User;

public enum UserRole {
    BIDDER,
    SELLER,
    ADMIN;
    public static UserRole fromString(String text) {
        if(text.equals("Người bán")){
            return SELLER;
        }
        if (text.equals("Người đấu giá")){
            return BIDDER;
        }
        return null; // Hoặc trả về một Role mặc định
    }
}
