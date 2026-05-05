package support;

import java.lang.reflect.Constructor;
import java.time.Instant;

import model.Items.Art;
import model.User.Bidder;
import model.User.Seller;
import model.auction.Auction;
import model.auction.AuctionManager;

public final class AuctionTestFixtures {
    private static final String DEFAULT_PASSWORD = "secret1";

    private AuctionTestFixtures() {
    }

    public static Seller seller(String id, String username) {
        return new Seller(id, "Seller " + username, username, DEFAULT_PASSWORD);
    }

    public static Bidder bidder(String id, String username) {
        return new Bidder(id, "Bidder " + username, username, DEFAULT_PASSWORD);
    }

    public static Art artItem(
            String itemId,
            String sellerId,
            double startingPrice,
            Instant startTime,
            Instant endTime
    ) {
        return new Art(
                itemId,
                "Test item " + itemId,
                "Description for " + itemId,
                startingPrice,
                startTime,
                endTime,
                sellerId,
                "Test artist"
        );
    }

    public static Auction auction(
            String auctionId,
            String itemId,
            Seller seller,
            double startingPrice,
            Instant startTime,
            Instant endTime
    ) {
        return new Auction(
                auctionId,
                artItem(itemId, seller.getId(), startingPrice, startTime, endTime),
                seller
        );
    }

    public static AuctionManager newAuctionManager() {
        try {
            Constructor<AuctionManager> constructor = AuctionManager.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create isolated AuctionManager for tests.", exception);
        }
    }
}
