package model.Items;

// Item đang chọn.
public class ItemSession {
    private static Item loggedInItem;

    public static void setLoggedInItem(Item item) {
        loggedInItem = item;
    }

    public static Item getLoggedInItem() {
        return loggedInItem;
    }
    // Dọn trạng thái.
    public static void cleanItemSession() {
        loggedInItem = null;
    }
}
