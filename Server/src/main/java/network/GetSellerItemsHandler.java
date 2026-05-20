package network;

import dao.DAOAuction_Items;
import dao.DAOItems;
import model.Items.Item;
import model.User.User;
import model.auction.AuctionStatus;
import service.UserService;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetSellerItemsHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public GetSellerItemsHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        // Khởi tạo map phản hồi kết quả về cho Client
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Lấy thông tin Username từ payload gửi lên (Giả định Client gửi lên là String username)
            String sellerUsername = String.valueOf(payload);

            if (sellerUsername == null || sellerUsername.trim().isEmpty()) {
                throw new IllegalArgumentException("Username không hợp lệ!");
            }

            // 2. Gọi DAO lấy danh sách Item theo Seller trực tiếp dưới Database
            ArrayList<Item> sellerItems = DAOItems.getInstance().selectBySellerId(sellerUsername);

            // 3. Tạo một Map lưu trữ trạng thái đấu giá (statusCache) đi kèm của từng Item
            Map<Integer, String> statusCache = new HashMap<>();

            if (sellerItems != null) {
                for (Item item : sellerItems) {
                    // Truy vấn bảng Đấu giá để lấy trạng thái hiện tại (OPEN, RUNNING, FINISHED,...)
                    var auctionItem = DAOAuction_Items.getInstance().selectByItemId(item);
                    if (auctionItem != null && auctionItem.getStatus() != null) {
                        // Lưu name() của Enum để khi nén qua mạng Client dễ đọc
                        statusCache.put(item.getDatabaseId(), auctionItem.getStatus().name());
                    } else {
                        // Mặc định nếu không thấy phiên đấu giá
                        statusCache.put(item.getDatabaseId(), AuctionStatus.OPEN.name());
                    }
                }
            } else {
                sellerItems = new ArrayList<>();
            }

            // 4. Đóng gói dữ liệu thành công gửi về cho Client
            response.put("success", true);
            response.put("items", sellerItems);
            response.put("statusCache", statusCache);

        } catch (Exception e) {
            e.printStackTrace();
            // Hàm dùng chung từ BaseHandler để gán thông tin lỗi
            fillErrorResponse(response, e);
        }

        // 5. Trả dữ liệu về Client thông qua ObjectOutputStream
        // Giả định bạn đã thêm GET_SELLER_ITEMS_RESULT vào Enum Command
        sendResponse(out, Command.GET_SELLER_ITEMS_RESULT, response);
    }
}