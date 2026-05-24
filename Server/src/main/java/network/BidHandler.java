package network;

import model.Items.Item;
import model.auction.Auction;
import model.auction.BidTransaction;
import model.exception.AuctionException;
import service.UserService;

import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BidHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public BidHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> bidInfo = (Map<String, Object>) payload;
        Map<String, Object> response = new HashMap<>();

        try {
            String itemId = String.valueOf(bidInfo.get("itemId"));
            String bidderId = String.valueOf(bidInfo.get("bidderId"));
            double amount = Double.parseDouble(String.valueOf(bidInfo.get("amount")));

            // Gọi service xử lý đấu giá và nhận về Map chứa các thông tin liên quan
            Map<String, Object> result = userService.processBid(itemId, bidderId, amount);

            if (result != null) {
                ArrayList<BidTransaction> bidHistory = (ArrayList<BidTransaction>) result.get("bidHistory");
                Auction latestAuction = (Auction) result.get("latestAuction");
                Item item = (Item) result.get("item");
                double newPrice = (double) result.get("newPrice");

                // 1. Chuẩn bị response trả về riêng cho client vừa đặt bid
                response.put("success", true);
                response.put("message", "Đấu giá thành công");
                response.put("newPrice", newPrice);
                response.put("itemId", itemId);

                // 2. Chuẩn bị gói tin cập nhật (Broadcast) cho các client đang xem chi tiết item này
                Map<String, Object> bidUpdate = new HashMap<>();
                bidUpdate.put("success", true);
                bidUpdate.put("itemId", itemId);
                bidUpdate.put("bidderId", bidderId);
                bidUpdate.put("newPrice", newPrice);
                bidUpdate.put("item", item);

                if (latestAuction != null) {
                    bidUpdate.put("auction", latestAuction);
                    Instant auctionEndTime = latestAuction.getItem() != null
                            ? latestAuction.getItem().getAuctionEndTime()
                            : null;
                    if (auctionEndTime != null) {
                        response.put("auctionEndTime", auctionEndTime);
                        bidUpdate.put("auctionEndTime", auctionEndTime);
                    }

                    // 3. Xác định người bị vượt giá (Người đặt giá cao thứ nhì trước đó)
                    String usernameOldBidder = null;
                    try {
                        if (bidHistory != null && bidHistory.size() >= 2) {
                            usernameOldBidder = bidHistory.get(bidHistory.size() - 2).getBidder();
                        } else {
                            usernameOldBidder = bidderId;
                        }
                    } catch (Exception e) {
                        usernameOldBidder = bidderId;
                    }

                    // Chuẩn bị payload gửi thông báo Toast dạng Notification
                    Map<String, Object> notifPayload = new HashMap<>();
                    notifPayload.put("item", item);
                    notifPayload.put("auction", latestAuction);
                    notifPayload.put("bidderId", bidderId);
                    notifPayload.put("newPrice", newPrice);

                    // --- TIẾN HÀNH PHÁT TÍN HIỆU REALTIME ---

                    // Phát tín hiệu cập nhật cho các client đang ở trong phòng đấu giá này
                    AuctionServer.broadcastToSpecificAuction(itemId, Command.BID_UPDATE, bidUpdate);

                    // Gửi cập nhật ra sảnh chính (itemId = null) để các client xem danh sách (Pagination) thấy giá mới
                    System.out.println("[Server Realtime] Phát tín hiệu cập nhật danh sách sảnh cho Item ID: " + itemId);
                    AuctionServer.broadcastToSpecificAuction(null, Command.ITEMS_UPDATE, latestAuction);

                    // Gửi thông báo đến người bị vượt giá và chủ phòng (Người bán)
                    AuctionServer.sendToSpecificUser(usernameOldBidder, Command.NOTIFICATION, notifPayload);
                    if (item != null && item.getSellerId() != null) {
                        AuctionServer.sendToSpecificUser(item.getSellerId(), Command.NOTIFICATION, notifPayload);
                    }
                } else {
                    System.err.println("[Server Lỗi] Không thể phát tín hiệu update vì không tìm thấy dữ liệu đấu giá hiện tại!");
                }
            } else {
                response.put("success", false);
                response.put("message", "Đấu giá thất bại");
            }
        } catch (AuctionException e) {
            fillErrorResponse(response, e);
            response.put("success", false);
            response.put("message", "Đấu giá thất bại: " + e.getMessage());
        } catch (Exception e) {
            fillErrorResponse(response, e);
            response.put("success", false);
            response.put("message", "Đấu giá thất bại do lỗi hệ thống");
        }

        // Gửi kết quả cuối cùng về cho người gửi Request ban đầu
        sendResponse(out, Command.BID_RESULT, response);
    }
}