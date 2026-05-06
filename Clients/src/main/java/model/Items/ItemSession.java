package model.Items;

/**
 * Session đơn giản (in-memory) lưu item hiện đang được chọn ở phía client.
 *
 * <p>Ví dụ:
 * - màn danh sách item -> click 1 card -> set vào session
 * - màn chi tiết/đấu giá -> đọc item từ session để hiển thị
 */
public class ItemSession {
    /** Item hiện tại; null nghĩa là chưa chọn hoặc đã clean session. */
    private static Item loggedInItem;

    /**
     * Precondition: {@code item} có thể null (để xoá session).
     * Postcondition: Gán {@code loggedInItem = item}.
     * Method returns: nothing.
     */
    public static void setLoggedInItem(Item item) {
        loggedInItem = item;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * Method returns: item hiện tại hoặc null.
     */
    public static Item getLoggedInItem() {
        return loggedInItem;
    }

    /**
     * Precondition: Không có.
     * Postcondition: {@code loggedInItem} = null.
     * NOTE: Thường gọi khi quay về danh sách hoặc khi logout.
     * Method returns: nothing.
     */
    public static void cleanItemSession() {
        loggedInItem = null; // Dùng khi Logout
    }
}