package model.Items;

/**
 * Session tĩnh lưu Item đang được chọn trong nhánh JavaFX UI.
 *
 * NOTE: Class này không phải session của socket server.
 */
public class ItemSession {
    /** Item hiện tại mà UI đang xem. */
    private static Item loggedInItem;

    /**
     * Precondition: item là item UI vừa chọn.
     * Postcondition: loggedInItem được gán bằng item.
     * Method không trả về giá trị.
     */
    public static void setLoggedInItem(Item item) {
        loggedInItem = item;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Method trả về item UI đang lưu, hoặc null nếu chưa chọn/đã clear.
     */
    public static Item getLoggedInItem() {
        return loggedInItem;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Xóa item đang lưu khỏi ItemSession.
     * Method không trả về giá trị.
     */
    public static void cleanItemSession() {
        loggedInItem = null; // Dùng khi Logout
    }
}
