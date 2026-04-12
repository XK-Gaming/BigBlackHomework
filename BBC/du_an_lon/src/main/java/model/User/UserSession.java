package model.User;

public class UserSession {
    private static User loggedInUser;

    public static void setLoggedInUser(User user) {
        loggedInUser = user;
    }

    public static User getLoggedInUser() {
        return loggedInUser;
    }

    public static void cleanUserSession() {
        loggedInUser = null; // Dùng khi Logout
    }
}