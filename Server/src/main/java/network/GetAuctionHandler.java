package network;

import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;

/**
 * Xử lý request GET_AUCTION.
 *
 * Payload mong đợi: Integer là id item.
 */
public class GetAuctionHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    /**
     * Precondition: userService đã được khởi tạo.
     * Postcondition: Handler có thể load dữ liệu auction theo item.
     */
    public GetAuctionHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    /**
     * Precondition: payload là Integer item id; out còn mở.
     * Postcondition: Gửi GET_AUCTION_RESULT với Auction object hoặc null.
     * Method không trả về giá trị.
     * NOTE: Có thể phát sinh ClassCastException nếu payload không phải Integer.
     */
    public void handle(Object payload, ObjectOutputStream out) {
        int itemId = (Integer) payload;
        String itemId_Str = String.valueOf(itemId);

        Auction auction = userService.getAuctionByItemId(itemId_Str);
        sendResponse(out, "GET_AUCTION_RESULT", auction);
    }
}
