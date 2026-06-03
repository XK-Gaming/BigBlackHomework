package model.auction;

import model.Items.Item;
import model.Items.ItemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuctionModelTest {

    // Test bid không nhận số tiền không dương.
    @Test
    void bidTransactionRejectsNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> new BidTransaction("bid-1", "bidder", 0, Instant.now()));
    }

    // Test lịch sử bid không bị sửa từ ngoài.
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

    // Test tạo auction thiếu item bị chặn.
    @Test
    void auctionConstructorRejectsMissingItem() {
        assertThrows(IllegalArgumentException.class,
                () -> new Auction("auction-1", null, "seller", Instant.now()));
    }

    // Test giá hiện tại dùng giá item khi chưa có bid.
    @Test
    void currentPriceFallsBackToItemPriceBeforeFirstBid() {
        Item item = new Item(
                "Watch",
                "Vintage watch",
                500,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                "seller",
                ItemType.ART,
                "watch.png");
        item.setCurrentHighestPrice(520);
        Auction auction = new Auction("auction-1", item, "seller", Instant.now());

        assertEquals(520, auction.getCurrentPrice(), 0.001);
    }

    // Test giá hiện tại lấy bid mới nhất.
    @Test
    void currentPriceUsesLatestBidTransaction() {
        Item item = new Item(
                "Laptop",
                "Gaming laptop",
                1000,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                "seller",
                ItemType.ELECTRONICS,
                "laptop.png");
        Auction auction = new Auction("auction-1", item, "seller", Instant.now());
        auction.setBidHistory(new ArrayList<>(List.of(
                new BidTransaction("bid-1", "bidder1", 1100, Instant.now()),
                new BidTransaction("bid-2", "bidder2", 1250, Instant.now()))));

        assertEquals(1250, auction.getCurrentPrice(), 0.001);
    }

    // Test so sánh auction theo itemId.
    @Test
    void equalityUsesItemIdInsteadOfObjectIdentity() {
        Item item = new Item(
                "Camera",
                "Mirrorless camera",
                700,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                "seller",
                ItemType.ELECTRONICS,
                "camera.png");
        Auction first = new Auction("auction-1", item, "seller", Instant.now());
        Auction second = new Auction("auction-2", item, "seller", Instant.now());
        Auction different = new Auction("auction-3", item, "seller", Instant.now());
        first.setItemId(42);
        second.setItemId(42);
        different.setItemId(43);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
    }
}
