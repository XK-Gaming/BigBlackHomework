package network;

import model.Items.Item;
import model.User.User;
import model.auction.Auction;
import model.auction.BidTransaction;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bid event: gom phần broadcast realtime để manual bid và AutoBid dùng chung cùng một format payload.
 */
final class BidEventPublisher {
    private BidEventPublisher() {
    }

    // Bid event: phát cập nhật giá, cập nhật danh sách và notification sau khi bid đã được DB chấp nhận.
    static void publishSuccessfulBid(String itemId, String bidderId, Map<String, Object> bidResult) {
        Auction latestAuction = (Auction) bidResult.get("latestAuction");
        Item item = (Item) bidResult.get("item");
        double newPrice = ((Number) bidResult.get("newPrice")).doubleValue();

        if (latestAuction == null) {
            System.err.println("[Server Error] Cannot broadcast bid update because latest auction is missing.");
            return;
        }

        Map<String, Object> bidUpdate = new HashMap<>();
        bidUpdate.put("success", true);
        bidUpdate.put("itemId", itemId);
        bidUpdate.put("bidderId", bidderId);
        bidUpdate.put("newPrice", newPrice);
        bidUpdate.put("item", item);
        bidUpdate.put("auction", latestAuction);

        Instant auctionEndTime = extractAuctionEndTime(latestAuction);
        if (auctionEndTime != null) {
            bidUpdate.put("auctionEndTime", auctionEndTime);
        }

        Map<String, Object> notifPayload = new HashMap<>();
        notifPayload.put("item", item);
        notifPayload.put("auction", latestAuction);
        notifPayload.put("bidderId", bidderId);
        notifPayload.put("newPrice", newPrice);

        AuctionServer.broadcastToSpecificAuction(itemId, Command.BID_UPDATE, bidUpdate);
        System.out.println("[Server Realtime] Broadcast list update for Item ID: " + itemId);
        AuctionServer.broadcastToSpecificAuction(null, Command.ITEMS_UPDATE, latestAuction);
        // Bid event: gửi thêm BID_UPDATE ra sảnh chung để các màn hình nền như lịch sử bid cũng bắt được giá mới.
        AuctionServer.broadcastToSpecificAuction(null, Command.BID_UPDATE, bidUpdate);

        sendBalanceUpdate(bidderId, userValue(bidResult.get("user")), null);
        sendBalanceUpdate(
                stringValue(bidResult.get("refundedBidderId")),
                userValue(bidResult.get("refundedUser")),
                bidResult.get("refundedBalance"));

        String oldBidder = findPreviousBidder(bidResult, bidderId);
        if (oldBidder != null && !oldBidder.isBlank() && !oldBidder.equals(bidderId)) {
            Map<String, Object> oldBidderPayload = new HashMap<>(notifPayload);
            if (oldBidder.equals(String.valueOf(bidResult.get("refundedBidderId")))) {
                oldBidderPayload.put("balance", bidResult.get("refundedBalance"));
                if (bidResult.get("refundedUser") instanceof User refundedUser) {
                    oldBidderPayload.put("user", refundedUser);
                }
            }
            AuctionServer.sendToSpecificUser(oldBidder, Command.NOTIFICATION, oldBidderPayload);
        }
        if (item != null && item.getSellerId() != null) {
            AuctionServer.sendToSpecificUser(item.getSellerId(), Command.NOTIFICATION, notifPayload);
        }
    }

    static Instant extractAuctionEndTime(Auction auction) {
        if (auction == null || auction.getItem() == null) {
            return null;
        }
        return auction.getItem().getAuctionEndTime();
    }

    @SuppressWarnings("unchecked")
    private static String findPreviousBidder(Map<String, Object> bidResult, String bidderId) {
        String refundedBidderId = stringValue(bidResult.get("refundedBidderId"));
        if (refundedBidderId != null) {
            return refundedBidderId;
        }

        try {
            List<BidTransaction> bidHistory = (List<BidTransaction>) bidResult.get("bidHistory");
            if (bidHistory != null && bidHistory.size() >= 2) {
                return bidHistory.get(bidHistory.size() - 2).getBidder();
            }
        } catch (Exception ignored) {
        }
        return bidderId;
    }

    private static void sendBalanceUpdate(String username, User user, Object balanceValue) {
        String targetUsername = stringValue(username);
        if (targetUsername == null && user != null) {
            targetUsername = stringValue(user.getUsername());
        }
        if (targetUsername == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("username", targetUsername);
        if (user != null) {
            payload.put("user", user);
            payload.put("balance", user.getBalance());
        } else if (balanceValue instanceof Number number) {
            payload.put("balance", number.doubleValue());
        } else if (balanceValue != null) {
            try {
                payload.put("balance", Double.parseDouble(String.valueOf(balanceValue)));
            } catch (NumberFormatException ignored) {
                return;
            }
        } else {
            return;
        }

        AuctionServer.sendToSpecificUser(targetUsername, Command.BALANCE_UPDATE, payload);
    }

    private static User userValue(Object value) {
        return value instanceof User user ? user : null;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }
}
