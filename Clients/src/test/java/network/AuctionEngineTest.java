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

class AuctionEngineTest {

    // Test item chưa tới giờ trả OPEN.
    @Test
    void watchItemReturnsOpenBeforeStartTime() throws Exception {
        Instant now = Instant.now();
        Item item = item(now.plusSeconds(60), now.plusSeconds(120));

        assertEquals(AuctionStatus.OPEN, firstStatus(item));
    }

    // Test item trong giờ đấu giá trả RUNNING.
    @Test
    void watchItemReturnsRunningBetweenStartAndEndTime() throws Exception {
        Instant now = Instant.now();
        Item item = item(now.minusSeconds(60), now.plusSeconds(60));

        assertEquals(AuctionStatus.RUNNING, firstStatus(item));
    }

    // Test item quá giờ trả FINISHED.
    @Test
    void watchItemReturnsFinishedAfterEndTime() throws Exception {
        Instant now = Instant.now();
        Item item = item(now.minusSeconds(120), now.minusSeconds(60));

        assertEquals(AuctionStatus.FINISHED, firstStatus(item));
    }

    // Test watchItem chặn tham số thiếu.
    @Test
    void watchItemRejectsMissingArguments() {
        assertNull(AuctionEngine.getInstance().watchItem(null, (status, seconds) -> { }));
        assertNull(AuctionEngine.getInstance().watchItem(item(Instant.now(), Instant.now()), null));
    }

    private AuctionStatus firstStatus(Item item) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AuctionStatus> firstStatus = new AtomicReference<>();

        int token = AuctionEngine.getInstance().watchItem(item, (status, seconds) -> {
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

    private Item item(Instant start, Instant end) {
        return new Item("Item", "Description", 100, start, end, "seller", ItemType.ART, "image.png");
    }
}
