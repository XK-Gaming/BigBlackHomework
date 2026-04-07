package trang_chu.du_an_lon.model;

public class UserSession {
    private static Person loggedInUser;

    public static void setLoggedInUser(Person person) {
        loggedInUser = person;
    }

    public static Person getLoggedInUser() {
        return loggedInUser;
    }

    public static void cleanUserSession() {
        loggedInUser = null; // Dùng khi Logout
    }
}