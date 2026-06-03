package service;

import dao.DAOAuction_Items;
import model.Items.Item;
import model.Items.ItemType;
import model.auction.Auction;
import model.auction.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionServiceTest {

    // Test sync status trả null khi thiếu auction.
    @Test
    void syncAuctionStatusReturnsNullWhenAuctionIsMissing() {
        assertNull(AuctionService.syncAuctionStatus(null));
    }

    // Test sync status trả trạng thái hiện tại.
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

    // Test getAuction tạo phiên khi chưa có.
    @Test
    void getAuctionCreatesAndPersistsAuctionWhenMissing() throws Exception {
        Item item = item(Instant.now().plusSeconds(60), Instant.now().plusSeconds(120));
        FakeAuctionDao auctionDao = new FakeAuctionDao();
        AuctionService service = serviceWith(auctionDao);

        Auction created = service.getAuction(item);

        assertSame(item, created.getItem());
        assertEquals(AuctionStatus.OPEN, created.getStatus());
        assertSame(created, auctionDao.insertedAuction);
        assertSame(item, auctionDao.insertedItem);
    }

    // Test phiên OPEN chuyển RUNNING khi tới giờ.
    @Test
    void getAuctionStartsOpenAuctionWhenStartTimeHasPassed() throws Exception {
        Item item = item(Instant.now().minusSeconds(60), Instant.now().plusSeconds(120));
        Auction existing = new StaticStatusAuction("auction-1", item, "seller", Instant.now(), AuctionStatus.OPEN);
        FakeAuctionDao auctionDao = new FakeAuctionDao();
        auctionDao.selectedAuction = existing;
        AuctionService service = serviceWith(auctionDao);

        Auction result = service.getAuction(item);

        assertSame(existing, result);
        assertEquals(AuctionStatus.RUNNING, result.getStatus());
        assertEquals(AuctionStatus.RUNNING, auctionDao.updatedStatus);
        assertSame(item, auctionDao.updatedItem);
    }

    // Test format giá có phân tách hàng nghìn.
    @Test
    void formatPriceUsesThousandsSeparator() {
        String formatted = new AuctionService().formatPrice(1234567);

        assertTrue(formatted.startsWith("1,234,567"));
        assertTrue(formatted.contains("VN"));
    }

    private AuctionService serviceWith(FakeAuctionDao auctionDao) throws Exception {
        AuctionService service = new AuctionService();
        setField(service, "auctionDAO", auctionDao);
        return service;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Item item(Instant start, Instant end) {
        Item item = new Item("Watch", "Vintage watch", 100, start, end, "seller", ItemType.ART, "watch.png");
        item.setDatabaseId(42);
        return item;
    }

    private static final class StaticStatusAuction extends Auction {
        private AuctionStatus status;

        private StaticStatusAuction(String id, Item item, String sellerId, Instant createdAt, AuctionStatus status) {
            super(id, item, sellerId, createdAt);
            setStatus(status);
        }

        @Override
        public AuctionStatus getStatus() {
            return status;
        }

        @Override
        public void setStatus(AuctionStatus status) {
            this.status = status;
            super.setStatus(status);
        }
    }

    private static final class FakeAuctionDao extends DAOAuction_Items {
        private Auction selectedAuction;
        private Auction insertedAuction;
        private Item insertedItem;
        private Item updatedItem;
        private AuctionStatus updatedStatus;

        @Override
        public Auction selectByItemId(Item item) {
            return selectedAuction;
        }

        @Override
        public int Insert(Auction auction, Item item) {
            this.insertedAuction = auction;
            this.insertedItem = item;
            return 1;
        }

        @Override
        public void Update_Status(Auction auction, Item item, AuctionStatus status) {
            auction.setStatus(status);
            this.updatedItem = item;
            this.updatedStatus = status;
        }
    }
}
