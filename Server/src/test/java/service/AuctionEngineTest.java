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

/**
 * ## JUnit: test service.AuctionEngine quet auction va nap item truoc khi ap dung rule thoi gian.
 */
class AuctionEngineTest {

    /**
     * ## Test luong nap du lieu: auction chua co Item thi engine lay Item theo itemId tu DAO.
     */
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

    /**
     * ## Test luong bo qua: auction khong co itemId hop le thi engine khong goi DAO va khong crash.
     */
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

    /**
     * ## Test luong rong: khong co auction thi engine ket thuc tick ngay.
     */
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
        return new Item(
                "Item",
                "Description",
                100,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                "seller",
                ItemType.ART,
                "image.png");
    }

    /**
     * ## Test fake service: tra ve danh sach auction de AuctionEngine xu ly ma khong can DB.
     */
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

    /**
     * ## Test fake DAO: ghi nhan itemId ma engine yeu cau nap.
     */
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
