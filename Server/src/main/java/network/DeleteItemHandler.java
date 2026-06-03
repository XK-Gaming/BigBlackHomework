package network;

import dao.DAOAuction_Items;
import dao.DAOItems;
import model.Items.Item;import model.auction.AuctionStatus;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

// Request xóa sản phẩm.
public class DeleteItemHandler extends BaseHandler implements RequestHandler {

    // Xử lý request xóa sản phẩm.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> response = new HashMap<>();

        try {

            if (!(payload instanceof Item)) {
                throw new IllegalArgumentException("Dữ liệu yêu cầu xóa không hợp lệ! Mong đợi đối tượng Item.");
            }

            Item selectedItem = (Item) payload;

            int itemId = selectedItem.getDatabaseId();

            var auctionItem = DAOAuction_Items.getInstance().selectByItemId(selectedItem);
            boolean hasBids = (auctionItem != null
                    && auctionItem.getBidHistory() != null
                    && !auctionItem.getBidHistory().isEmpty() && auctionItem.getStatus() == AuctionStatus.RUNNING);

            if (hasBids) {
                response.put("success", false);
                response.put("message", "Không thể xóa! Sản phẩm này đã có người tham gia đặt giá.");

                sendResponse(out, Command.DELETE_ITEM_RESULT, response);
                return;
            }
            if (auctionItem != null) {
                DAOAuction_Items.getInstance().Delete(selectedItem);
            }

            int rowsAffected = DAOItems.getInstance().Delete(selectedItem);

            if (rowsAffected > 0) {

                response.put("success", true);
                response.put("message", "Xóa sản phẩm thành công!");
                response.put("deletedItemId", itemId);
                response.put("itemName", selectedItem.getName());

                Map<String, Object> broadcastData = new HashMap<>();
                broadcastData.put("success", true);
                broadcastData.put("deletedItemId", itemId);
                broadcastData.put("itemName", selectedItem.getName());

                System.out.println("[Server Realtime] Phát tín hiệu XÓA sản phẩm ra sảnh chính cho Item ID: " + itemId);
                AuctionServer.broadcastToSpecificAuction(null, Command.ITEMS_UPDATE, broadcastData);

                Map<String, Object> roomData = new HashMap<>();
                roomData.put("success", false);
                roomData.put("message", "Sản phẩm (ID: " + itemId + ") đã bị gỡ bỏ bởi ban quản trị hoặc người bán.");
                roomData.put("isForceClose", true);

                System.out.println("[Server] Đã gửi thông báo đóng phòng đấu giá " + itemId + " tới các client đang xem.");
                AuctionServer.broadcastToSpecificAuction(String.valueOf(itemId), Command.DELETE_ITEM_RESULT, roomData);

            } else {
                response.put("success", false);
                response.put("message", "Không tìm thấy sản phẩm trong cơ sở dữ liệu để xóa.");
            }

            sendResponse(out, Command.DELETE_ITEM_RESULT, response);

        } catch (Exception e) {
            e.printStackTrace();
            fillErrorResponse(response, e);
            response.put("success", false);
            response.put("message", "Xóa thất bại do lỗi hệ thống.");
            sendResponse(out, Command.DELETE_ITEM_RESULT, response);
        }
    }
}
