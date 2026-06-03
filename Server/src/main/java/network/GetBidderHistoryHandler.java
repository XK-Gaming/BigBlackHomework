package network;

import service.UserService;
import model.auction.BidHistoryDTO;
import java.io.ObjectOutputStream;
import java.util.List;

// Request lịch sử bidder.
public class GetBidderHistoryHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public GetBidderHistoryHandler(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request lịch sử bidder.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        try {

            String username = (String) payload;

            List<BidHistoryDTO> historyList = userService.getBidderHistory(username);

            sendResponse(out, Command.GET_BIDDER_HISTORY_RESULT, historyList);

        } catch (Exception e) {
            System.err.println("Lỗi tại GetBidderHistoryHandler: " + e.getMessage());
            e.printStackTrace();

        }
    }
}
