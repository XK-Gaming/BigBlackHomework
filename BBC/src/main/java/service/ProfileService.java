package service;

import model.User.Seller;
import model.User.User;
import model.User.UserSession;
import model.exception.AuthenticationException;

public final class ProfileService {
    public User requireCurrentUser() {
        User user = UserSession.getLoggedInUser();
        if (user == null) {
            throw new AuthenticationException("No active session.");
        }
        return user;
    }

    public User getCurrentUserOrNull() {
        return UserSession.getLoggedInUser();
    }

    public String homeView(User user) {
        if (user instanceof Seller) {
            return "View3.1.fxml";
        }
        return "View3.fxml";
    }

    public String maskedPassword() {
        return "********";
    }
}
