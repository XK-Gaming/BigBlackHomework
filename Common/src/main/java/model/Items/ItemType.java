package model.Items;

// Loại sản phẩm.
public enum ItemType {
    ART, ELECTRONICS, VEHICLE;
    // Đổi chuỗi sang enum.
    public static ItemType fromString(String text) {
        if (text == null) return null;
        String trimmed = text.trim();

        if (trimmed.equalsIgnoreCase("Mỹ thuật") || trimmed.equalsIgnoreCase("ART")) {
            return ItemType.ART;
        }
        if (trimmed.equalsIgnoreCase("Điện tử") || trimmed.equalsIgnoreCase("ELECTRONICS")) {
            return ItemType.ELECTRONICS;
        }
        if (trimmed.equalsIgnoreCase("Phương tiện giao thông") || trimmed.equalsIgnoreCase("VEHICLE")) {
            return ItemType.VEHICLE;
        }
        return null;
    }
}
