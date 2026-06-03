package service;

import dao.DAOItems;
import model.Items.Item;
import model.auction.Auction;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Theo dõi trạng thái phiên.
public final class AuctionEngine implements AutoCloseable {

    private static final long TICK_SECONDS = Long.getLong("auction.engine.tick.seconds", 5);

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final UserService userService;
    private final DAOItems items;
    private final ScheduledExecutorService scheduler;

    public AuctionEngine() {
        this(new UserService(), dao.DAOItems.getInstance());
    }

    public AuctionEngine(UserService userService, DAOItems items) {
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
    // Bật engine quét phiên.
    public void startEngine() {
        scheduler.scheduleAtFixedRate(this::safeTick, 2, TICK_SECONDS, TimeUnit.SECONDS);
    }
    // Quét phiên an toàn.
    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            System.err.println("[AuctionEngine] Tick lỗi: " + t.getMessage());
            t.printStackTrace();
        }
    }
    // Quét trạng thái.
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
    // Cập nhật trạng thái theo giờ.
    private void applyTimeRules(Auction auction) {
        if (auction == null) {
            return;
        }

        long pk = auction.getItemId();
        if (pk > 0) {
            Item freshItem = items.selectById(String.valueOf(pk));
            if (freshItem != null) {

                auction.setItem(freshItem);
            }
        }

        auction.getStatus();
    }
    // Dừng xử lý.
    public void stopEngine() {
        scheduler.shutdown();
    }

    @Override
    public void close() {
        stopEngine();
    }
}
