package network;

import service.UserService;
import model.auction.BidHistoryDTO; // Class DTO chứa thông tin card lịch sử
import java.io.ObjectOutputStream;
import java.util.List;

public class GetBidderHistoryHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public GetBidderHistoryHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        try {
            // Payload gửi lên từ client là username (String) của Bidder
            String username = (String) payload;

            // Gọi service xử lý logic lọc lịch sử đấu giá dựa trên danh sách đấu giá
            // (Bạn cần bổ sung hàm này trong UserService hoặc viết trực tiếp logic lọc tại đây)
            List<BidHistoryDTO> historyList = userService.getBidderHistory(username);

            // Gửi phản hồi về Client với Command kết quả tương ứng
            sendResponse(out, Command.GET_BIDDER_HISTORY_RESULT, historyList);

        } catch (Exception e) {
            System.err.println("Lỗi tại GetBidderHistoryHandler: " + e.getMessage());
            e.printStackTrace();
            // Bạn có thể gửi một gói lỗi nếu muốn: sendResponse(out, Command.ERROR, "Lỗi Server");
        }
    }
}