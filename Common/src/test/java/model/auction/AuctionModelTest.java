package model.auction;

import model.Items.Item;
import model.Items.ItemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ## JUnit: test cac rule co ban cua model auction va bid history.
 */
class AuctionModelTest {

    /**
     * ## Test bid khong hop le: amount <= 0 phai bi tu choi ngay tai BidTransaction.
     */
    @Test
    void bidTransactionRejectsNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> new BidTransaction("bid-1", "bidder", 0, Instant.now()));
    }

    /**
     * ## Test dong goi du lieu: getBidHistory khong cho code ben ngoai sua truc tiep list noi bo.
     */
    @Test
    void bidHistoryCannotBeModifiedFromGetter() {
        Item item = new Item(
                "Phone",
                "Used phone",
                100,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                "seller",
                ItemType.ELECTRONICS,
                "phone.png");
        Auction auction = new Auction("auction-1", item, "seller", Instant.now());
        BidTransaction bid = new BidTransaction("bid-1", "bidder", 120, Instant.now());

        auction.setBidHistory(new ArrayList<>(List.of(bid)));

        assertEquals(1, auction.getBidHistory().size());
        assertThrows(UnsupportedOperationException.class,
                () -> auction.getBidHistory().add(new BidTransaction("bid-2", "other", 130, Instant.now())));
    }
}
