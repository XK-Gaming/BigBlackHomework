package controller;

import model.User.Bidder;
import model.User.User;
import model.User.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserBalanceSyncTest {

    @AfterEach
    void cleanSession() {
        UserSession.cleanUserSession();
    }

    // Test đồng bộ số dư đúng user hiện tại.
    @Test
    void appliesMatchingUserPayloadToCurrentSession() {
        User sessionUser = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 1_000);
        User updatedUser = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 850);
        UserSession.setLoggedInUser(sessionUser);

        boolean updated = UserBalanceSync.applyBalancePayload(Map.of("user", updatedUser));

        assertTrue(updated);
        assertSame(sessionUser, UserSession.getLoggedInUser());
        assertEquals(850.0, UserSession.getLoggedInUser().getBalance());
    }

    // Test hoàn tiền khi user bị vượt bid.
    @Test
    void appliesRefundedBalanceWhenCurrentUserWasOutbid() {
        User sessionUser = new Bidder("oldBidder", "secret", "Old Bidder", "old@example.com", 300);
        UserSession.setLoggedInUser(sessionUser);

        Map<String, Object> payload = new HashMap<>();
        payload.put("bidderId", "newBidder");
        payload.put("refundedBidderId", "oldBidder");
        payload.put("refundedBalance", 450.0);

        boolean updated = UserBalanceSync.applyBalancePayload(payload);

        assertTrue(updated);
        assertEquals(450.0, UserSession.getLoggedInUser().getBalance());
    }

    // Test bỏ qua payload số dư của user khác.
    @Test
    void ignoresBalancePayloadForAnotherUser() {
        User sessionUser = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 1_000);
        UserSession.setLoggedInUser(sessionUser);

        boolean updated = UserBalanceSync.applyBalancePayload(Map.of(
                "username", "bidder2",
                "balance", 100.0));

        assertFalse(updated);
        assertEquals(1_000.0, UserSession.getLoggedInUser().getBalance());
    }
}
