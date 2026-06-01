package network;

import model.Items.Item;
import model.User.User;
import model.auction.Auction;
import model.auction.AuctionStatus;
import service.UserService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AutoBid: lưu cấu hình đang bật trong bộ nhớ server và tự kiểm tra đặt giá mỗi 5 giây.
 */
public final class AutoBidManager {
    private static final long CHECK_INTERVAL_SECONDS = 5;
    private static final AutoBidManager INSTANCE = new AutoBidManager(new UserService());
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();

    private final UserService userService;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<AutoBidKey, AutoBidRegistration> registrations = new ConcurrentHashMap<>();

    private AutoBidManager(UserService userService) {
        this.userService = Objects.requireNonNull(userService);
        this.scheduler = Executors.newScheduledThreadPool(2, daemonFactory());
    }

    public static AutoBidManager getInstance() {
        return INSTANCE;
    }

    // AutoBid: đăng ký cấu hình mới, tạo lịch kiểm tra 5 giây và chạy kiểm tra đầu tiên ngay.
    public Map<String, Object> enable(String itemId, String username, double maxBidAllow, double bidGap) {
        AutoBidConfig config = new AutoBidConfig(
                normalize(itemId),
                normalize(username),
                maxBidAllow,
                bidGap);

        validateConfig(config);

        Auction auction = userService.getAuctionByItemId(config.itemId());
        if (auction == null || auction.getItem() == null) {
            return errorResponse(config, "Khong tim thay phien dau gia.");
        }

        double currentPrice = currentPrice(auction);
        if (currentPrice >= config.maxBidAllow()) {
            return errorResponse(config, "MaxBidAllow phai lon hon gia hien tai.");
        }
        if (config.bidGap() < minBid(auction)) {
            return errorResponse(config, "BidGap phải lớn hơn hoặc bằng MinBid của sản phẩm.");
        }

        AutoBidKey key = config.key();
        AutoBidRegistration registration = new AutoBidRegistration(config);
        AutoBidRegistration oldRegistration = registrations.put(key, registration);
        if (oldRegistration != null) {
            oldRegistration.cancel();
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> safeEvaluateAndNotify(key),
                CHECK_INTERVAL_SECONDS,
                CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        registration.setFuture(future);

        AutoBidAttempt attempt = evaluate(key);
        return response(config, attempt);
    }

    // AutoBid: tắt một cấu hình cụ thể khi user bấm OFF hoặc khi rule không còn hợp lệ.
    public Map<String, Object> disable(String itemId, String username, String message) {
        AutoBidConfig config = new AutoBidConfig(
                normalize(itemId),
                normalize(username),
                0,
                0);
        disable(config.key());
        Map<String, Object> response = baseResponse(config);
        response.put("success", true);
        response.put("enabled", false);
        response.put("message", message == null || message.isBlank() ? "AutoBid da tat." : message);
        return response;
    }

    // AutoBid: dọn toàn bộ cấu hình của user khi logout hoặc disconnect.
    public void disableAllForUser(String username, String reason) {
        String normalizedUsername = normalize(username);
        registrations.forEach((key, registration) -> {
            if (key.username().equals(normalizedUsername)) {
                disable(key);
            }
        });
        System.out.println("[AutoBid] Disabled all configs for user " + normalizedUsername + ": " + reason);
    }

    // AutoBid: wrapper an toàn cho scheduler, đồng thời gửi trạng thái về user khi có thay đổi cần báo.
    private void safeEvaluateAndNotify(AutoBidKey key) {
        AutoBidRegistration registration = registrations.get(key);
        if (registration == null) {
            return;
        }

        try {
            AutoBidAttempt attempt = evaluate(key);
            if (attempt.shouldNotifyUser()) {
                AuctionServer.sendToSpecificUser(
                        registration.config().username(),
                        Command.SET_AUTO_BID_RESULT,
                        response(registration.config(), attempt));
            }
        } catch (Exception e) {
            disable(key);
            AuctionServer.sendToSpecificUser(
                    registration.config().username(),
                    Command.SET_AUTO_BID_RESULT,
                    disabledResponse(registration.config(), "AutoBid da dung do loi he thong: " + e.getMessage(), false));
        }
    }

    // AutoBid: kiểm tra leading bidder/current price/max và đặt bid qua UserService.processBid nếu đủ điều kiện.
    private AutoBidAttempt evaluate(AutoBidKey key) {
        AutoBidRegistration registration = registrations.get(key);
        if (registration == null) {
            return AutoBidAttempt.disabled("AutoBid da tat.", false);
        }

        AutoBidConfig config = registration.config();
        Auction auction = userService.getAuctionByItemId(config.itemId());
        if (auction == null || auction.getItem() == null) {
            disable(key);
            return AutoBidAttempt.disabled("Khong tim thay phien dau gia. AutoBid da tat.", true);
        }

        AuctionStatus status = auction.getStatus();
        if (status != AuctionStatus.RUNNING) {
            disable(key);
            return AutoBidAttempt.disabled("Phien dau gia khong con dang dien ra. AutoBid da tat.", true);
        }

        String leadingBidder = auction.getLeadingBidder();
        if (config.username().equals(leadingBidder)) {
            return AutoBidAttempt.skipped("Ban dang la nguoi dan dau.");
        }

        double currentPrice = currentPrice(auction);
        if (currentPrice >= config.maxBidAllow()) {
            disable(key);
            return AutoBidAttempt.disabled("Gia hien tai da dat toi MaxBidAllow. AutoBid da tat.", true);
        }

        double minAllowedBid = minAllowedBid(auction, currentPrice);
        if (config.maxBidAllow() < minAllowedBid) {
            disable(key);
            return AutoBidAttempt.disabled("MaxBidAllow không đủ để đặt giá tối thiểu (giá hiện tại + MinBid). AutoBid đã tắt.", true);
        }

        double bidAmount = Math.min(currentPrice + config.bidGap(), config.maxBidAllow());
        if (bidAmount <= currentPrice) {
            disable(key);
            return AutoBidAttempt.disabled("Gia AutoBid khong hop le. AutoBid da tat.", true);
        }
        if (bidAmount < minAllowedBid) {
            disable(key);
            return AutoBidAttempt.disabled("Giá AutoBid thấp hơn bước MinBid. AutoBid đã tắt.", true);
        }

        try {
            Map<String, Object> bidResult = userService.processBid(config.itemId(), config.username(), bidAmount);
            BidEventPublisher.publishSuccessfulBid(config.itemId(), config.username(), bidResult);
            return AutoBidAttempt.placed("AutoBid da dat gia thanh cong.", bidAmount, bidResult);
        } catch (Exception e) {
            disable(key);
            return AutoBidAttempt.disabled("AutoBid dat gia that bai: " + e.getMessage(), true, false);
        }
    }

    private void disable(AutoBidKey key) {
        AutoBidRegistration removed = registrations.remove(key);
        if (removed != null) {
            removed.cancel();
        }
    }

    private static ThreadFactory daemonFactory() {
        return task -> {
            Thread thread = new Thread(task, "auto-bid-check-" + THREAD_SEQ.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void validateConfig(AutoBidConfig config) {
        if (config.itemId().isBlank()) {
            throw new IllegalArgumentException("itemId khong hop le.");
        }
        if (config.username().isBlank()) {
            throw new IllegalArgumentException("user khong hop le.");
        }
        if (config.maxBidAllow() <= 0) {
            throw new IllegalArgumentException("MaxBidAllow phai lon hon 0.");
        }
        if (config.bidGap() <= 0) {
            throw new IllegalArgumentException("BidGap phai lon hon 0.");
        }
    }

    private static double currentPrice(Auction auction) {
        Item item = auction.getItem();
        return item != null ? item.getCurrentHighestPrice() : auction.getCurrentPrice();
    }

    private static double minBid(Auction auction) {
        Item item = auction.getItem();
        return item == null ? 0 : Math.max(0, item.getMinBid());
    }

    private static double minAllowedBid(Auction auction, double currentPrice) {
        if (isFirstBid(auction)) {
            return Math.nextUp(currentPrice);
        }
        return currentPrice + minBid(auction);
    }

    private static boolean isFirstBid(Auction auction) {
        String leadingBidder = auction.getLeadingBidder();
        boolean hasLeader = leadingBidder != null && !leadingBidder.isBlank() && !"null".equalsIgnoreCase(leadingBidder);
        boolean hasHistory = auction.getBidHistory() != null && !auction.getBidHistory().isEmpty();
        return !hasLeader && !hasHistory;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> errorResponse(AutoBidConfig config, String message) {
        Map<String, Object> response = baseResponse(config);
        response.put("success", false);
        response.put("enabled", false);
        response.put("message", message);
        return response;
    }

    private static Map<String, Object> disabledResponse(AutoBidConfig config, String message, boolean success) {
        Map<String, Object> response = baseResponse(config);
        response.put("success", success);
        response.put("enabled", false);
        response.put("message", message);
        return response;
    }

    private boolean isEnabled(AutoBidConfig config) {
        return registrations.containsKey(config.key());
    }

    private Map<String, Object> response(AutoBidConfig config, AutoBidAttempt attempt) {
        Map<String, Object> response = baseResponse(config);
        response.put("success", attempt.success());
        response.put("enabled", isEnabled(config));
        response.put("message", attempt.message());
        response.put("bidPlaced", attempt.bidPlaced());
        if (attempt.bidAmount() > 0) {
            response.put("bidAmount", attempt.bidAmount());
        }
        if (attempt.user() != null) {
            response.put("user", attempt.user());
            response.put("balance", attempt.user().getBalance());
        }
        return response;
    }

    private static Map<String, Object> baseResponse(AutoBidConfig config) {
        Map<String, Object> response = new HashMap<>();
        response.put("itemId", config.itemId());
        response.put("username", config.username());
        response.put("maxBidAllow", config.maxBidAllow());
        response.put("bidGap", config.bidGap());
        return response;
    }

    private record AutoBidKey(String itemId, String username) {
    }

    private record AutoBidConfig(String itemId, String username, double maxBidAllow, double bidGap) {
        AutoBidKey key() {
            return new AutoBidKey(itemId, username);
        }
    }

    private static final class AutoBidRegistration {
        private final AutoBidConfig config;
        private volatile ScheduledFuture<?> future;

        private AutoBidRegistration(AutoBidConfig config) {
            this.config = config;
        }

        private AutoBidConfig config() {
            return config;
        }

        private void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        private void cancel() {
            ScheduledFuture<?> currentFuture = future;
            if (currentFuture != null) {
                currentFuture.cancel(false);
            }
        }
    }

    private record AutoBidAttempt(
            boolean success,
            boolean bidPlaced,
            double bidAmount,
            User user,
            String message,
            boolean shouldNotifyUser) {

        static AutoBidAttempt placed(String message, double bidAmount, Map<String, Object> bidResult) {
            User updatedUser = bidResult != null && bidResult.get("user") instanceof User user ? user : null;
            return new AutoBidAttempt(true, true, bidAmount, updatedUser, message, true);
        }

        static AutoBidAttempt skipped(String message) {
            return new AutoBidAttempt(true, false, 0, null, message, false);
        }

        static AutoBidAttempt disabled(String message, boolean shouldNotifyUser) {
            return disabled(message, shouldNotifyUser, true);
        }

        static AutoBidAttempt disabled(String message, boolean shouldNotifyUser, boolean success) {
            return new AutoBidAttempt(success, false, 0, null, message, shouldNotifyUser);
        }
    }
}
