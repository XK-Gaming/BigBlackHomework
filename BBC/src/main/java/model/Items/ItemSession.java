package model.Items;

public class ItemSession {
    private static Item loggedInItem;

    public static void setLoggedInItem(Item item) {
        loggedInItem = item;
    }

    public static Item getLoggedInItem() {
        return loggedInItem;
    }

    public static void cleanItemSession() {
        loggedInItem = null; // Dùng khi Logout
    }
}