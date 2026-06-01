package service;

import dao.DAOAuction_Items;
import dao.DAOItems;
import model.Items.Item;
import model.auction.Auction;
import model.auction.AuctionStatus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    private void applyTimeRules(Auction auction) {
        if (auction == null) return;

        // 🛡️ CHỐNG SIDE-EFFECT: Đóng băng trạng thái thô nguyên bản của DB ngay lập tức
        AuctionStatus oldStatus = auction.getRawStatus();

        Item item = auction.getItem();
        if (item == null) {
            long pk = auction.getItemId();
            if (pk <= 0) return;
            item = items.selectById(String.valueOf(pk));
            if (item == null) return;

            // Gán liên kết thủ công
            auction.setItem(item);

            // Nếu việc nạp item hoặc gán item vô tình làm thay đổi status trong RAM,
            // chúng ta ép lại oldStatus thô ban đầu để đảm bảo tính chuẩn xác cho tầng so sánh bên dưới
            if (oldStatus == null) {
                oldStatus = auction.getRawStatus();
            }
        }

        // 2. Ép hệ thống tính toán trạng thái mới dựa trên mốc thời gian thực tại (Instant.now())
        auction.updateStatusByTime();

        // 3. Đọc trạng thái mới sau khi áp dụng quy tắc
        AuctionStatus newStatus = auction.getRawStatus();

        // 4. Kiểm tra sự dịch chuyển trạng thái vòng đời
        if (oldStatus != newStatus) {
            // Cập nhật trạng thái xuống Cơ sở dữ liệu
            DAOAuction_Items.getInstance().Update_Status(auction, item, newStatus);
            System.out.printf("[AuctionEngine] Đã cập nhật trạng thái phiên id_item %d: %s -> %s%n",
                    auction.getItemId(), oldStatus, newStatus);

            try {
                String itemIdStr = String.valueOf(auction.getItemId());

                java.util.Map<String, Object> updatePayload = new java.util.HashMap<>();

                // Đồng bộ kiểu int (Integer) khớp hoàn toàn với Client side tránh lệch kiểu dữ liệu
                updatePayload.put("itemId", (int) auction.getItemId());
                updatePayload.put("newStatus", newStatus.name());

                // Kích hoạt bắn gói tin Real-time qua hạ tầng Network Socket xuống người dùng
                network.AuctionServer.broadcastToSpecificAuction(itemIdStr, network.Command.UPDATE_AUCTION_STATUS, updatePayload);

                System.out.println("[AuctionEngine] -> Đã phát tín hiệu Real-time cập nhật trạng thái tới các Client thành công.");
            } catch (Exception e) {
                System.err.println("[AuctionEngine] Không thể broadcast trạng thái: " + e.getMessage());
            }
        }
    }

    public void stopEngine() {
        scheduler.shutdown();
    }

    @Override
    public void close() {
        stopEngine();
    }
}