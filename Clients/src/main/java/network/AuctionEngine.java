package network;

import model.Items.Item;
import model.auction.AuctionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Engine client-side cập nhật trạng thái phiên đấu giá theo thời gian.
 * Dùng cho UI danh sách để tự đổi trạng thái OPEN/RUNNING/FINISHED realtime.
 */
public class AuctionEngine {
    public interface AuctionStatusListener {
        void onStatus(AuctionStatus status, long secondsToNextChange);
    }

    private static final AuctionEngine INSTANCE = new AuctionEngine();
    private final Map<String, WatchRegistration> registrations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

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

    public String watchItem(Item item, AuctionStatusListener listener) {
        if (item == null || listener == null) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        registrations.put(token, new WatchRegistration(item, listener));
        notifySingle(item, listener);
        return token;
    }

    public void unwatch(String token) {
        if (token != null) {
            registrations.remove(token);
        }
    }

    private void tick() {
        for (WatchRegistration reg : registrations.values()) {
            notifySingle(reg.item(), reg.listener());
        }
    }

    private void notifySingle(Item item, AuctionStatusListener listener) {
        Instant now = Instant.now();
        AuctionStatus status;
        long secondsToNextChange;

        if (now.isBefore(item.getAuctionStartTime())) {
            status = AuctionStatus.OPEN;
            secondsToNextChange = Duration.between(now, item.getAuctionStartTime()).getSeconds();
        } else if (now.isBefore(item.getAuctionEndTime())) {
            status = AuctionStatus.RUNNING;
            secondsToNextChange = Duration.between(now, item.getAuctionEndTime()).getSeconds();
        } else {
            status = AuctionStatus.FINISHED;
            secondsToNextChange = 0;
        }
        listener.onStatus(status, Math.max(0, secondsToNextChange));
    }

    private record WatchRegistration(Item item, AuctionStatusListener listener) {}}