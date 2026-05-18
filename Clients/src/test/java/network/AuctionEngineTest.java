package network;

import model.Items.Item;
import model.Items.ItemType;
import model.auction.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ## JUnit: test AuctionEngine phia client tinh trang thai OPEN/RUNNING/FINISHED theo thoi gian.
 */
class AuctionEngineTest {

    /**
     * ## Test phien chua bat dau: item co startTime tuong lai phai la OPEN.
     */
    @Test
    void watchItemReturnsOpenBeforeStartTime() throws Exception {
        Instant now = Instant.now();
        Item item = item(now.plusSeconds(60), now.plusSeconds(120));

        assertEquals(AuctionStatus.OPEN, firstStatus(item));
    }

    /**
     * ## Test phien dang dien ra: now nam giua start/end phai la RUNNING.
     */
    @Test
    void watchItemReturnsRunningBetweenStartAndEndTime() throws Exception {
        Instant now = Instant.now();
        Item item = item(now.minusSeconds(60), now.plusSeconds(60));

        assertEquals(AuctionStatus.RUNNING, firstStatus(item));
    }

    /**
     * ## Test phien da ket thuc: now sau endTime phai la FINISHED.
     */
    @Test
    void watchItemReturnsFinishedAfterEndTime() throws Exception {
        Instant now = Instant.now();
        Item item = item(now.minusSeconds(120), now.minusSeconds(60));

        assertEquals(AuctionStatus.FINISHED, firstStatus(item));
    }

    /**
     * ## Test input null: watchItem tra null khi thieu item hoac listener.
     */
    @Test
    void watchItemRejectsMissingArguments() {
        assertNull(AuctionEngine.getInstance().watchItem(null, (status, seconds) -> { }));
        assertNull(AuctionEngine.getInstance().watchItem(item(Instant.now(), Instant.now()), null));
    }

    /**
     * ## Test helper: lay status dau tien tu listener roi huy dang ky watch.
     */
    private AuctionStatus firstStatus(Item item) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AuctionStatus> firstStatus = new AtomicReference<>();

        String token = AuctionEngine.getInstance().watchItem(item, (status, seconds) -> {
            if (firstStatus.compareAndSet(null, status)) {
                latch.countDown();
            }
        });

        try {
            assertTrue(latch.await(1, TimeUnit.SECONDS));
            return firstStatus.get();
        } finally {
            AuctionEngine.getInstance().unwatch(token);
        }
    }

    /**
     * ## Test helper: tao Item mau cho cac case tinh trang thai client.
     */
    private Item item(Instant start, Instant end) {
        return new Item("Item", "Description", 100, start, end, "seller", ItemType.ART, "image.png");
    }
}
