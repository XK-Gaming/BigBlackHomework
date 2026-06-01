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
        // 🚀 BỔ SUNG LOG 1: Kiểm tra xem định kỳ 5 giây Engine có thực sự thức dậy không
        System.out.println("[AuctionEngine] -> Bắt đầu một vòng quét thời gian thực mới...");

        if (userService == null) {
            System.err.println("[AuctionEngine Error] userService bị NULL, không thể quét!");
            return;
        }

        List<Auction> auctions = null;
        try {
            auctions = userService.getAllAuctions();
        } catch (Exception e) {
            // 🚀 BỔ SUNG LOG 2: Bắt lỗi nếu hàm truy vấn Database bị crash
            System.err.println("[AuctionEngine Error] Lỗi nghiêm trọng khi gọi getAllAuctions() từ DB: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // 🚀 BỔ SUNG LOG 3: Kiểm tra số lượng bản ghi quét được từ database
        System.out.println("[AuctionEngine] Quét DB thành công. Tìm thấy tổng cộng: " + (auctions != null ? auctions.size() : 0) + " phiên.");

        if (auctions == null || auctions.isEmpty()) {
            return; // Thoát nếu không có hàng hóa nào
        }

        for (Auction auction : auctions) {
            try {
                applyTimeRules(auction);
            } catch (Exception e) {
                System.err.println("[AuctionEngine] Bỏ qua một phiên do lỗi quy tắc thời gian: " + e.getMessage());
            }
        }
    }

    private void applyTimeRules(Auction auction) {
        if (auction == null) return;

        AuctionStatus oldStatus = auction.getRawStatus();
        Item item = auction.getItem();

        if (item == null) {
            long pk = auction.getItemId();
            if (pk <= 0) return;

            try {
                item = items.selectById(String.valueOf(pk));
            } catch (Exception e) {
                System.err.println("[AuctionEngine] Lỗi khi nạp Item cho Phiên ID " + pk + ": " + e.getMessage());
                return;
            }

            if (item == null) {
                System.out.println("[AuctionEngine] Không tìm thấy Item trong DB cho Phiên ID " + pk);
                return;
            }
            auction.setItem(item);
            if (oldStatus == null) {
                oldStatus = auction.getRawStatus();
            }
        }


        // 2. Ép hệ thống tính toán trạng thái mới dựa trên thời gian
        auction.updateStatusByTime();

        // 3. Đọc trạng thái mới sau khi áp dụng quy tắc
        AuctionStatus newStatus = auction.getRawStatus();
// 4. Kiểm tra sự dịch chuyển trạng thái vòng đời
        if (oldStatus != newStatus) {
            dao.DAOAuction_Items.getInstance().Update_Status(auction, item, newStatus);
            System.out.printf("[AuctionEngine] Đã cập nhật trạng thái phiên id_item %d: %s -> %s%n",
                    auction.getItemId(), oldStatus, newStatus);

            try {
                String itemIdStr = String.valueOf(auction.getItemId());
                java.util.Map<String, Object> updatePayload = new java.util.HashMap<>();

                // Ép kiểu int để khớp với colId (Integer) bên Client
                updatePayload.put("itemId", (int) auction.getItemId());
                updatePayload.put("newStatus", newStatus.name());

                // 1. Phát vào phòng cụ thể (giữ nguyên của bạn)
                network.AuctionServer.broadcastToSpecificAuction(itemIdStr, network.Command.UPDATE_AUCTION_STATUS, updatePayload);

                // 🌟 SỬA/BỔ SUNG DÒNG NÀY: Phát ra SẢNH CHUNG (null) để màn hình danh sách nhận được!
                network.AuctionServer.broadcastToSpecificAuction(null, network.Command.UPDATE_AUCTION_STATUS, updatePayload);

                System.out.println("[AuctionEngine] -> Đã phát tín hiệu Real-time trạng thái ra sảnh chung.");
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