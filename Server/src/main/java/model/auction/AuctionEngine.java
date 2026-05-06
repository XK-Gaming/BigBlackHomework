package model.auction;

import service.UserService;
import dao.DAOItems;
import model.Items.Item;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiến trình nền trên máy chủ: định kỳ tải mọi phiên đấu giá từ DB, gắn {@link Item}
 * và chạy {@link Auction#updateStatusByTime()} để OPEN → RUNNING → FINISHED (đã ghi DB trong model).
 */
public final class AuctionEngine implements AutoCloseable {

    /** Chu kỳ quét — có thể ghi JVM property {@code auction.engine.tick.seconds}. */
    private static final long TICK_SECONDS = Long.getLong("auction.engine.tick.seconds", 5);

    /** Bộ đếm dùng để đặt tên thread nền của engine. */
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** Service dùng để lấy danh sách auction. */
    private final UserService userService;
    /** DAO dùng để load Item khi Auction chỉ có itemId. */
    private final DAOItems items;
    /** Scheduler chạy tick định kỳ. */
    private final ScheduledExecutorService scheduler;

    /**
     * Precondition: Không có.
     * Postcondition: Tạo AuctionEngine với UserService và DAOItems mặc định.
     */
    public AuctionEngine() {
        this(new UserService(), DAOItems.getInstance());
    }

    /**
     * Precondition: userService và items khác null.
     * Postcondition: Tạo AuctionEngine với dependency được truyền vào và một scheduler riêng.
     * NOTE: Constructor này dùng tốt cho test vì có thể inject dependency.
     */
    AuctionEngine(UserService userService, DAOItems items) {
        this.userService = Objects.requireNonNull(userService);
        this.items = Objects.requireNonNull(items);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(namedDaemonFactory());
    }

    /**
     * Precondition: Không có.
     * Postcondition: Method trả về ThreadFactory tạo daemon thread có tên auction-engine-tick-N.
     */
    private static ThreadFactory namedDaemonFactory() {
        return r -> {
            Thread t = new Thread(r, "auction-engine-tick-" + SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** Bắt đầu quét sau 2 giây, lặp mỗi {@link #TICK_SECONDS} giây. */
    /**
     * Precondition: scheduler chưa bị shutdown.
     * Postcondition: Đăng ký task safeTick chạy sau 2 giây và lặp lại mỗi TICK_SECONDS giây.
     * Method không trả về giá trị.
     */
    public void startEngine() {
        scheduler.scheduleAtFixedRate(this::safeTick, 2, TICK_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Precondition: Engine đang được scheduler gọi định kỳ.
     * Postcondition: Gọi tick(); nếu có lỗi thì bắt Throwable và ghi log để scheduler không chết.
     * Method không trả về giá trị.
     */
    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            System.err.println("[AuctionEngine] Tick lỗi: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Precondition: userService có thể truy vấn danh sách auction.
     * Postcondition: Mỗi auction lấy được sẽ được áp dụng rule thời gian qua applyTimeRules().
     * Method không trả về giá trị.
     */
    void tick() {
        List<Auction> auctions = userService.getAllAuctions();
        if (auctions == null || auctions.isEmpty()) {
            return;
        }
        for (Auction auction : auctions) {
            try {
                applyTimeRules(auction);
            } catch (Exception e) {
                System.err.println("[AuctionEngine] Bỏ qua một phiên: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /** Nạp item theo khóa id_item, sau đó cập nhật trạng thái theo thời gian hiện tại (và DAO nếu đổi trạng thái). */
    /**
     * Precondition: auction có thể null; nếu không null thì cần có item hoặc itemId hợp lệ.
     * Postcondition: Nếu load được item, method gọi auction.getStatus() để cập nhật trạng thái
     * theo thời gian và đồng bộ DB nếu cần.
     * Method không trả về giá trị.
     */
    private void applyTimeRules(Auction auction) {
        if (auction == null) {
            return;
        }
        Item item = auction.getItem();
        if (item == null) {
            long pk = auction.getItemId();
            if (pk <= 0) {
                return;
            }
            item = items.selectById(String.valueOf(pk));
            if (item == null) {
                return;
            }
            auction.setItem(item);
        }
        /* updateStatusByTime được gọi từ getStatus(); đồng bộ OPEN/RUNNING/FINISHED vào DB khi có thay đổi */
        auction.getStatus();
    }

    /** Dừng lịch; gọi khi tắt ứng dụng server (JavaFX). */
    /**
     * Precondition: Engine đã được tạo.
     * Postcondition: scheduler được shutdown.
     * Method không trả về giá trị.
     */
    public void stopEngine() {
        scheduler.shutdown();
    }

    @Override
    /**
     * Precondition: Engine đã được tạo.
     * Postcondition: Gọi stopEngine() để dừng scheduler.
     * Method không trả về giá trị.
     */
    public void close() {
        stopEngine();
    }
}
