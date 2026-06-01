package controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import model.User.User;
import model.User.UserSession;

import java.text.DecimalFormat;
import java.util.Map;

final class UserBalanceSync {
    private UserBalanceSync() {
    }

    static boolean applyBalancePayload(Object payload) {
        if (!(payload instanceof Map<?, ?> data)) {
            return false;
        }

        User currentUser = UserSession.getLoggedInUser();
        if (currentUser == null || currentUser.getUsername() == null) {
            return false;
        }

        User updatedUser = userValue(data.get("user"));
        if (updatedUser == null) {
            updatedUser = userValue(data.get("refundedUser"));
        }

        if (updatedUser != null) {
            if (!currentUser.getUsername().equals(updatedUser.getUsername())) {
                return false;
            }
            currentUser.setBalance(updatedUser.getBalance());
            UserSession.setLoggedInUser(currentUser);
            return true;
        }

        String targetUsername = firstNonBlank(
                stringValue(data.get("username")),
                stringValue(data.get("userId")),
                stringValue(data.get("refundedBidderId")),
                stringValue(data.get("bidderId")));

        if (targetUsername != null && !currentUser.getUsername().equals(targetUsername)) {
            return false;
        }

        Double balance = numericValue(data.get("balance"));
        if (balance == null && currentUser.getUsername().equals(stringValue(data.get("refundedBidderId")))) {
            balance = numericValue(data.get("refundedBalance"));
        }
        if (balance == null) {
            balance = numericValue(data.get("newBalance"));
        }
        if (balance == null) {
            return false;
        }

        currentUser.setBalance(balance);
        UserSession.setLoggedInUser(currentUser);
        return true;
    }

    static void refreshBalanceLabel(Label balanceLabel) {
        User currentUser = UserSession.getLoggedInUser();
        if (currentUser == null || balanceLabel == null) {
            return;
        }

        Runnable update = () -> {
            DecimalFormat df = new DecimalFormat("#,###");
            balanceLabel.setText(df.format(currentUser.getBalance()) + " VNĐ");
        };

        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    static boolean applyAndRefresh(Object payload, Label balanceLabel) {
        boolean updated = applyBalancePayload(payload);
        if (updated) {
            refreshBalanceLabel(balanceLabel);
        }
        return updated;
    }

    private static User userValue(Object value) {
        return value instanceof User user ? user : null;
    }

    private static Double numericValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
