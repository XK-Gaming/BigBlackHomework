package network;

import javafx.application.Platform;
import model.Items.Item;
import model.auction.AuctionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

// Theo dõi trạng thái phiên.
public class AuctionEngine {
    public interface AuctionStatusListener {
        void onStatus(AuctionStatus status, long secondsToNextChange);
    }

    private final Map<Integer, WatchRegistration> registrations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private static final AuctionEngine INSTANCE = new AuctionEngine();

    private AuctionEngine() {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "client-auction-engine");
            t.setDaemon(true);
            return t;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(factory);
        scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    public static AuctionEngine getInstance() {
        return INSTANCE;
    }
    // Theo dõi item.
    public Integer watchItem(Item item, AuctionStatusListener listener) {
        if (item == null || listener == null) {
            return null;
        }
        int itemId = item.getDatabaseId();
        registrations.put(itemId, new WatchRegistration(item, listener));

        notifySingle(item, listener);
        return itemId;
    }
    // Dừng theo dõi item.
    public void unwatchItem(Item item) {
        if (item != null) {
            registrations.remove(item.getDatabaseId());
        }
    }

    public void unwatch(String itemIdStr) {
        if (itemIdStr != null) {
            try {
                int itemId = Integer.parseInt(itemIdStr.trim());
                registrations.remove(itemId);
            } catch (NumberFormatException e) {
                System.err.println("[AuctionEngine] Không thể unwatch vì chuỗi ID không hợp lệ: " + itemIdStr);
            }
        }
    }

    public void unwatch(int itemId) {
        registrations.remove(itemId);
    }
    // Quét trạng thái.
    private void tick() {
        for (WatchRegistration reg : registrations.values()) {
            notifySingle(reg.item(), reg.listener());
        }
    }
    // Báo trạng thái mới.
    private void notifySingle(Item item, AuctionStatusListener listener) {
        if (item == null || listener == null) {
            return;
        }

        Instant now = Instant.now();
        final AuctionStatus status;
        final long secondsToNextChange;

        Instant startTime = item.getAuctionStartTime();
        Instant endTime = item.getAuctionEndTime();

        if (startTime == null || endTime == null) {
            status = AuctionStatus.FINISHED;
            secondsToNextChange = 0;
        } else if (now.isBefore(startTime)) {
            status = AuctionStatus.OPEN;
            secondsToNextChange = Duration.between(now, startTime).getSeconds();
        } else if (now.isBefore(endTime)) {
            status = AuctionStatus.RUNNING;
            secondsToNextChange = Duration.between(now, endTime).getSeconds();
        } else {
            status = AuctionStatus.FINISHED;
            secondsToNextChange = 0;
        }

        runOnFxThreadOrNow(() -> {
            item.updateStatus(status);
            listener.onStatus(status, Math.max(0, secondsToNextChange));
        });
    }
    // Chạy trên UI thread.
    private void runOnFxThreadOrNow(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException e) {
            action.run();
        }
    }

    private record WatchRegistration(Item item, AuctionStatusListener listener) {}
}
