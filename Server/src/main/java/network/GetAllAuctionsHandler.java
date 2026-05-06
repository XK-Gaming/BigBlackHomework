package network;

import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;
import java.util.List;

/**
 * Xử lý request GET_ALL_AUCTIONS.
 *
 * Payload mong đợi: không sử dụng.
 */
public class GetAllAuctionsHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    /**
     * Precondition: userService đã được khởi tạo.
     * Postcondition: Handler có thể truy vấn toàn bộ auction.
     */
    public GetAllAuctionsHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    /**
     * Precondition: out còn mở; payload bị bỏ qua.
     * Postcondition: Gửi GET_ALL_AUCTIONS_RESULT với List<Auction>.
     * Method không trả về giá trị.
     */
    public void handle(Object payload, ObjectOutputStream out) {
        List<Auction> auctions = userService.getAllAuctions();
        sendResponse(out, "GET_ALL_AUCTIONS_RESULT", auctions);
    }
}
