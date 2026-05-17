package service;

import model.Items.Item;
import model.Items.ItemType;
import model.auction.Auction;
import model.auction.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ## JUnit: test logic nho cua AuctionService ma khong truy cap database.
 */
class AuctionServiceTest {

    /**
     * ## Test null safety: khong co auction thi syncAuctionStatus tra null.
     */
    @Test
    void syncAuctionStatusReturnsNullWhenAuctionIsMissing() {
        assertNull(AuctionService.syncAuctionStatus(null));
    }

    /**
     * ## Test trang thai phien: lay dung status hien tai cua Auction.
     */
    @Test
    void syncAuctionStatusReturnsCurrentAuctionStatus() {
        Item item = new Item(
                "Watch",
                "Vintage watch",
                100,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                "seller",
                ItemType.ART,
                "watch.png");
        Auction auction = new Auction("auction-1", item, "seller", Instant.now());
        auction.setStatus(AuctionStatus.FINISHED);

        assertEquals(AuctionStatus.FINISHED, AuctionService.syncAuctionStatus(auction));
    }
}
