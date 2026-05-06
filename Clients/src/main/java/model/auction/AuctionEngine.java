package model.auction;

import model.Items.Item;

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
        /**
         * Precondition: {@code status} khác null; {@code secondsToNextChange >= 0}.
         * Postcondition: Listener tự cập nhật UI/state theo trạng thái.
         * NOTE: Callback có thể được gọi từ luồng nền của engine; nếu cập nhật UI JavaFX cần {@code Platform.runLater}.
         * Method returns: nothing.
         */
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

    /**
     * Precondition: Không có.
     * Postcondition: Trả về singleton instance.
     * Method returns: {@link AuctionEngine}.
     */
    public static AuctionEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Precondition: {@code item} và {@code listener} khác null.
     * Postcondition:
     * - Tạo một đăng ký theo dõi mới (watch) và lưu vào {@code registrations}.
     * - Gọi {@link #notifySingle(Item, AuctionStatusListener)} ngay 1 lần để UI có trạng thái ban đầu.
     * NOTE: Nếu {@code item} hoặc {@code listener} null -> trả về null và không đăng ký.
     * Method returns: token (String) để dùng cho {@link #unwatch(String)}; hoặc null.
     */
    public String watchItem(Item item, AuctionStatusListener listener) {
        if (item == null || listener == null) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        registrations.put(token, new WatchRegistration(item, listener));
        notifySingle(item, listener);
        return token;
    }

    /**
     * Precondition: {@code token} có thể null.
     * Postcondition: Nếu token tồn tại trong map thì đăng ký watch tương ứng bị gỡ bỏ.
     * NOTE: Sau khi unwatch, callback sẽ không còn được gọi nữa.
     * Method returns: nothing.
     */
    public void unwatch(String token) {
        if (token != null) {
            registrations.remove(token);
        }
    }

    /**
     * Precondition: Không có.
     * Postcondition: Gọi {@link #notifySingle(Item, AuctionStatusListener)} cho toàn bộ watch registrations.
     * NOTE: Được scheduler gọi mỗi giây.
     * Method returns: nothing.
     */
    private void tick() {
        for (WatchRegistration reg : registrations.values()) {
            notifySingle(reg.item(), reg.listener());
        }
    }

    /**
     * Precondition: {@code item} và {@code listener} khác null; item có start/end time hợp lệ.
     * Postcondition: Xác định status dựa trên {@link Instant#now()} so với start/end, rồi gọi listener.
     * NOTE: Status chỉ dựa trên thời gian; không xét các trạng thái "logic" khác như PAID/CANCELED ở phía server.
     * Method returns: nothing.
     */
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

    private record WatchRegistration(Item item, AuctionStatusListener listener) {}
}
