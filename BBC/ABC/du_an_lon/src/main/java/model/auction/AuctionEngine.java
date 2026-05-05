package model.auction;

import Service_.UserService;
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

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final UserService userService;
    private final DAOItems items;
    private final ScheduledExecutorService scheduler;

    public AuctionEngine() {
        this(new UserService(), DAOItems.getInstance());
    }

    AuctionEngine(UserService userService, DAOItems items) {
        this.userService = Objects.requireNonNull(userService);
        this.items = Objects.requireNonNull(items);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(namedDaemonFactory());
    }

    private static ThreadFactory namedDaemonFactory() {
        return r -> {
            Thread t = new Thread(r, "auction-engine-tick-" + SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** Bắt đầu quét sau 2 giây, lặp mỗi {@link #TICK_SECONDS} giây. */
    public void startEngine() {
        scheduler.scheduleAtFixedRate(this::safeTick, 2, TICK_SECONDS, TimeUnit.SECONDS);
    }

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            System.err.println("[AuctionEngine] Tick lỗi: " + t.getMessage());
            t.printStackTrace();
        }
    }

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
    public void stopEngine() {
        scheduler.shutdown();
    }

    @Override
    public void close() {
        stopEngine();
    }
}
