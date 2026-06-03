package service;

import dao.DAOItems;
import model.Items.Item;
import model.Items.ItemType;
import model.auction.Auction;
import model.auction.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuctionEngineTest {

    // Test engine nạp item trước khi áp luật thời gian.
    @Test
    void tickHydratesAuctionItemBeforeApplyingTimeRules() {
        Auction auction = new Auction();
        auction.setItemId(42);
        auction.setStatus(AuctionStatus.OPEN);
        Item item = item();
        FakeUserService userService = new FakeUserService(List.of(auction));
        FakeItemDao itemDao = new FakeItemDao(item);

        try (AuctionEngine engine = new AuctionEngine(userService, itemDao)) {
            engine.tick();
        }

        assertEquals("42", itemDao.selectedId);
        assertSame(item, auction.getItem());
    }

    // Test engine bỏ qua auction thiếu itemId.
    @Test
    void tickSkipsAuctionWithoutValidItemId() {
        Auction auction = new Auction();
        auction.setStatus(AuctionStatus.OPEN);
        FakeUserService userService = new FakeUserService(List.of(auction));
        FakeItemDao itemDao = new FakeItemDao(item());

        try (AuctionEngine engine = new AuctionEngine(userService, itemDao)) {
            engine.tick();
        }

        assertNull(itemDao.selectedId);
        assertNull(auction.getItem());
    }

    // Test engine không lỗi khi không có phiên.
    @Test
    void tickReturnsWhenThereAreNoAuctions() {
        FakeUserService userService = new FakeUserService(List.of());
        FakeItemDao itemDao = new FakeItemDao(item());

        try (AuctionEngine engine = new AuctionEngine(userService, itemDao)) {
            engine.tick();
        }

        assertNull(itemDao.selectedId);
    }

    private Item item() {
        Instant now = Instant.now();
        return new Item(
                "Item",
                "Description",
                100,
                now.plusSeconds(60),
                now.plusSeconds(120),
                "seller",
                ItemType.ART,
                "image.png");
    }

    private static final class FakeUserService extends UserService {
        private final List<Auction> auctions;

        private FakeUserService(List<Auction> auctions) {
            this.auctions = auctions;
        }

        @Override
        public List<Auction> getAllAuctions() {
            return auctions;
        }
    }

    private static final class FakeItemDao extends DAOItems {
        private final Item item;
        private String selectedId;

        private FakeItemDao(Item item) {
            this.item = item;
        }

        @Override
        public Item selectById(String itemId) {
            this.selectedId = itemId;
            return item;
        }
    }
}
