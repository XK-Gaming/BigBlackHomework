package network;

import model.User.UserRole;import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;
import java.util.List;

// Request lấy toàn bộ phiên.
public class GetAllAuctionsHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public GetAllAuctionsHandler(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request lấy toàn bộ phiên.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        List<Auction> auctions = userService.getAllAuctions();
        sendResponse(out, Command.GET_ALL_AUCTIONS_RESULT, auctions);
    }
}
