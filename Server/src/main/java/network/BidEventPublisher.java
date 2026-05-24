package network;

import model.Items.Item;
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

        String oldBidder = findPreviousBidder(bidResult, bidderId);
        AuctionServer.sendToSpecificUser(oldBidder, Command.NOTIFICATION, notifPayload);
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
        try {
            List<BidTransaction> bidHistory = (List<BidTransaction>) bidResult.get("bidHistory");
            if (bidHistory != null && bidHistory.size() >= 2) {
                return bidHistory.get(bidHistory.size() - 2).getBidder();
            }
        } catch (Exception ignored) {
            // Keep the previous behavior: fall back to the new bidder when history is unavailable.
        }
        return bidderId;
    }
}
