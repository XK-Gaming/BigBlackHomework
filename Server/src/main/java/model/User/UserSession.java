package model.User;

/**
 * Session tĩnh lưu User đang đăng nhập trong nhánh JavaFX UI.
 *
 * NOTE: Class này không quản lý kết nối socket online; phần đó nằm ở AuctionServer.onlineClients.
 */
public class UserSession {
    /** User hiện tại của UI. */
    private static User loggedInUser;

    /**
     * Precondition: user là user vừa đăng nhập ở UI.
     * Postcondition: loggedInUser được gán bằng user.
     * Method không trả về giá trị.
     */
    public static void setLoggedInUser(User user) {
        loggedInUser = user;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Method trả về user hiện tại, hoặc null nếu chưa đăng nhập/đã logout.
     */
    public static User getLoggedInUser() {
        return loggedInUser;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Xóa user khỏi session UI.
     * Method không trả về giá trị.
     */
    public static void cleanUserSession() {
        loggedInUser = null; // Dùng khi Logout
    }
}
