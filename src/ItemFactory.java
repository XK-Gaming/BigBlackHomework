public class ItemFactory {
    public static Item createItem(String id, ItemType type, Seller seller, String name,
                                  String description, double startingPrice, String extraInfo) {
        if (type == null) {
            throw new IllegalArgumentException("Loai san pham khong hop le.");
        }

        return switch (type) {
            case ELECTRONICS -> new Electronics(id, seller, name, description, startingPrice, extraInfo);
            case ART -> new Art(id, seller, name, description, startingPrice, extraInfo);
            case VEHICLE -> new Vehicle(id, seller, name, description, startingPrice, extraInfo);
        };
    }
}
