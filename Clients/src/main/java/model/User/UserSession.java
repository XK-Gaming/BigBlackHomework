package model.User;

/**
 * Session đơn giản (in-memory) lưu thông tin người dùng đang đăng nhập ở phía client.
 *
 * <p>Ràng buộc:
 * <ul>
 *   <li>static: dữ liệu dùng chung toàn bộ app.</li>
 *   <li>không thread-safe: phù hợp với UI app đơn; nếu dùng đa luồng cần đồng bộ.</li>
 * </ul>
 */
public class UserSession {
    /** User hiện tại; null nghĩa là chưa login hoặc đã logout. */
    private static User loggedInUser;

    /**
     * Precondition: {@code user} có thể null (để xoá session).
     * Postcondition: Gán {@code loggedInUser = user}.
     * Method returns: nothing.
     */
    public static void setLoggedInUser(User user) {
        loggedInUser = user;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không thay đổi state.
     * Method returns: user hiện tại hoặc null.
     */
    public static User getLoggedInUser() {
        return loggedInUser;
    }

    /**
     * Precondition: Không có.
     * Postcondition: {@code loggedInUser} được set về null (logout).
     * Method returns: nothing.
     */
    public static void cleanUserSession() {
        loggedInUser = null; // Dùng khi Logout
    }
}