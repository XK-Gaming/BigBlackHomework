package model.User;

// User đang đăng nhập.
public class UserSession {
    private static User loggedInUser;

    public static void setLoggedInUser(User user) {
        loggedInUser = user;
    }

    public static User getLoggedInUser() {
        return loggedInUser;
    }
    // Dọn trạng thái.
    public static void cleanUserSession() {
        loggedInUser = null;
    }
}
