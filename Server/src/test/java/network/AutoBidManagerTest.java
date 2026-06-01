package network;

import model.Items.Item;
import model.Items.ItemType;
import model.User.Bidder;
import model.User.User;
import model.auction.Auction;
import model.auction.AuctionStatus;
import org.junit.jupiter.api.Test;
import service.UserService;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoBidManagerTest {

    /**
     * ## Test AutoBid bat thanh cong: manager dat bid ngay lap tuc bang current price + bidGap.
     */
    @Test
    void enablePlacesImmediateBidUsingConfiguredGap() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auction = auction(AuctionStatus.RUNNING, "other", 100, 10);
        AutoBidManager manager = managerWith(userService);

        try {
            Map<String, Object> response = manager.enable(" 7 ", " bidder1 ", 130, 15);

            assertEquals(true, response.get("success"));
            assertEquals(true, response.get("enabled"));
            assertEquals(true, response.get("bidPlaced"));
            assertEquals(115.0, (Double) response.get("bidAmount"), 0.001);
            assertEquals("7", response.get("itemId"));
            assertEquals("bidder1", response.get("username"));
            assertEquals(885.0, response.get("balance"));
            assertEquals("bidder1", ((User) response.get("user")).getUsername());
            assertEquals(1, userService.processBidCalls);
            assertEquals("7", userService.processItemId);
            assertEquals("bidder1", userService.processBidderId);
            assertEquals(115.0, userService.processAmount, 0.001);
        } finally {
            manager.disable("7", "bidder1", "cleanup");
        }
    }

    /**
     * ## Test AutoBid gioi han max: bid tu dong khong duoc vuot qua MaxBidAllow.
     */
    @Test
    void enableCapsImmediateBidAtMaxBidAllow() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auction = auction(AuctionStatus.RUNNING, "other", 100, 10);
        AutoBidManager manager = managerWith(userService);

        try {
            Map<String, Object> response = manager.enable("7", "bidder1", 125, 50);

            assertEquals(true, response.get("success"));
            assertEquals(true, response.get("bidPlaced"));
            assertEquals(125.0, (Double) response.get("bidAmount"), 0.001);
            assertEquals(125.0, userService.processAmount, 0.001);
        } finally {
            manager.disable("7", "bidder1", "cleanup");
        }
    }

    /**
     * ## Test AutoBid khi dang dan dau: khong dat them bid neu user da la leading bidder.
     */
    @Test
    void enableSkipsBidWhenUserAlreadyLeads() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auction = auction(AuctionStatus.RUNNING, "bidder1", 100, 10);
        AutoBidManager manager = managerWith(userService);

        try {
            Map<String, Object> response = manager.enable("7", "bidder1", 150, 20);

            assertEquals(true, response.get("success"));
            assertEquals(true, response.get("enabled"));
            assertEquals(false, response.get("bidPlaced"));
            assertEquals(0, userService.processBidCalls);
        } finally {
            manager.disable("7", "bidder1", "cleanup");
        }
    }

    /**
     * ## Test validate MaxBidAllow: khong cho bat AutoBid neu max khong lon hon gia hien tai.
     */
    @Test
    void enableRejectsMaxBidAtOrBelowCurrentPrice() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auction = auction(AuctionStatus.RUNNING, "other", 100, 10);
        AutoBidManager manager = managerWith(userService);

        Map<String, Object> response = manager.enable("7", "bidder1", 100, 20);

        assertEquals(false, response.get("success"));
        assertEquals(false, response.get("enabled"));
        assertEquals(0, userService.processBidCalls);
    }

    /**
     * ## Test validate BidGap: khong cho bat AutoBid neu bidGap nho hon MinBid cua san pham.
     */
    @Test
    void enableRejectsBidGapBelowItemMinBid() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auction = auction(AuctionStatus.RUNNING, "other", 100, 25);
        AutoBidManager manager = managerWith(userService);

        Map<String, Object> response = manager.enable("7", "bidder1", 200, 10);

        assertEquals(false, response.get("success"));
        assertEquals(false, response.get("enabled"));
        assertEquals(0, userService.processBidCalls);
    }

    /**
     * ## Test auction khong RUNNING: AutoBid tu tat va khong goi processBid.
     */
    @Test
    void enableDisablesRegistrationWhenAuctionIsNotRunning() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auction = auction(AuctionStatus.FINISHED, "other", 100, 10);
        AutoBidManager manager = managerWith(userService);

        Map<String, Object> response = manager.enable("7", "bidder1", 200, 20);

        assertEquals(true, response.get("success"));
        assertEquals(false, response.get("enabled"));
        assertEquals(false, response.get("bidPlaced"));
        assertEquals(0, userService.processBidCalls);
    }

    /**
     * ## Test processBid loi: AutoBid tu tat va tra response loi ve client.
     */
    @Test
    void enableDisablesRegistrationWhenProcessBidFails() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auction = auction(AuctionStatus.RUNNING, "other", 100, 10);
        userService.failure = new RuntimeException("rejected");
        AutoBidManager manager = managerWith(userService);

        Map<String, Object> response = manager.enable("7", "bidder1", 200, 20);

        assertEquals(false, response.get("success"));
        assertEquals(false, response.get("enabled"));
        assertEquals(false, response.get("bidPlaced"));
        assertEquals(1, userService.processBidCalls);
    }

    /**
     * ## Test tat AutoBid: disable tra enabled=false va message duoc truyen vao.
     */
    @Test
    void disableReturnsDisabledSuccessResponse() throws Exception {
        AutoBidManager manager = managerWith(new FakeUserService());

        Map<String, Object> response = manager.disable("7", "bidder1", "done");

        assertEquals(true, response.get("success"));
        assertEquals(false, response.get("enabled"));
        assertEquals("done", response.get("message"));
    }

    /**
     * ## Test config khong hop le: manager nem IllegalArgumentException truoc khi goi service.
     */
    @Test
    void enableRejectsInvalidConfigBeforeCallingService() throws Exception {
        FakeUserService userService = new FakeUserService();
        AutoBidManager manager = managerWith(userService);

        assertThrows(IllegalArgumentException.class,
                () -> manager.enable("", "bidder1", 100, 10));
        assertThrows(IllegalArgumentException.class,
                () -> manager.enable("7", "", 100, 10));
        assertThrows(IllegalArgumentException.class,
                () -> manager.enable("7", "bidder1", 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> manager.enable("7", "bidder1", 100, 0));
        assertEquals(0, userService.getAuctionCalls);
    }

    /**
     * ## Test cleanup khi logout: disableAllForUser chi xoa registration cua user do.
     */
    @Test
    void disableAllForUserRemovesOnlyThatUsersRegistrations() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auction = auction(AuctionStatus.RUNNING, "other", 100, 10);
        AutoBidManager manager = managerWith(userService);
        manager.enable("7", "bidder1", 200, 20);
        manager.enable("7", "bidder2", 220, 20);

        manager.disableAllForUser("bidder1", "logout");

        assertEquals(1, registrationCount(manager));
        Map<String, Object> bidder2Off = manager.disable("7", "bidder2", "cleanup");
        assertEquals(false, bidder2Off.get("enabled"));
        assertEquals(0, registrationCount(manager));
        assertTrue(userService.processBidCalls >= 2);
    }

    private AutoBidManager managerWith(UserService userService) throws Exception {
        Constructor<AutoBidManager> constructor = AutoBidManager.class.getDeclaredConstructor(UserService.class);
        constructor.setAccessible(true);
        return constructor.newInstance(userService);
    }

    private int registrationCount(AutoBidManager manager) throws Exception {
        var field = AutoBidManager.class.getDeclaredField("registrations");
        field.setAccessible(true);
        Map<?, ?> registrations = (Map<?, ?>) field.get(manager);
        return registrations.size();
    }

    private Auction auction(AuctionStatus status, String leadingBidder, double currentPrice, double minBid) {
        Instant now = Instant.now();
        Instant start = status == AuctionStatus.OPEN ? now.plusSeconds(60) : now.minusSeconds(60);
        Instant end = status == AuctionStatus.FINISHED ? now.minusSeconds(30) : now.plusSeconds(3600);
        Item item = new Item(
                "Item",
                "Description",
                currentPrice,
                minBid,
                start,
                end,
                "seller",
                ItemType.ART,
                "item.png");
        item.setDatabaseId(7);
        item.setCurrentHighestPrice(currentPrice);
        Auction auction = new Auction("auction-1", item, "seller", now);
        auction.setStatus(status);
        auction.setLeadingBidder(leadingBidder);
        return auction;
    }

    private static final class FakeUserService extends UserService {
        private Auction auction;
        private RuntimeException failure;
        private int getAuctionCalls;
        private int processBidCalls;
        private String processItemId;
        private String processBidderId;
        private double processAmount;

        @Override
        public Auction getAuctionByItemId(String itemId) {
            getAuctionCalls++;
            return auction;
        }

        @Override
        public Map<String, Object> processBid(String itemId, String bidderId, double amount) {
            processBidCalls++;
            processItemId = itemId;
            processBidderId = bidderId;
            processAmount = amount;
            if (failure != null) {
                throw failure;
            }
            if (auction != null) {
                auction.setLeadingBidder(bidderId);
                auction.getItem().setCurrentHighestPrice(amount);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("latestAuction", auction);
            result.put("item", auction == null ? null : auction.getItem());
            result.put("newPrice", amount);
            result.put("user", new Bidder(bidderId, "secret", "Bidder", bidderId + "@example.com", 1_000 - amount));
            result.put("bidHistory", new ArrayList<>());
            return result;
        }
    }
}
