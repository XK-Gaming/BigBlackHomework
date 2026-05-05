package model.Items;


public enum ItemType {
    ELECTRONICS,
    ART,
    VEHICLE;
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
