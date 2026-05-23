package model.Items;

public enum ItemType {
    ART, ELECTRONICS, VEHICLE;

    public static ItemType fromString(String text) {
        if (text == null) return null;
        String trimmed = text.trim();

        // Chấp nhận cả dữ liệu dạng tiếng Việt cũ lẫn định dạng Enum tiếng Anh mới
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