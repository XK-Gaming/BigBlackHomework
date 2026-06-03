package network;

import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;

// Request lấy phiên đấu giá.
public class GetAuctionHandler extends BaseHandler implements RequestHandler {
    private ClientHandler clientHandler;
    private final UserService userService;

    public GetAuctionHandler(UserService userService, ClientHandler clientHandler) {
        this.userService = userService;
        this.clientHandler = clientHandler;
    }

    // Xử lý request lấy phiên đấu giá.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        if (payload == null) {
            sendResponse(out, Command.GET_AUCTION_RESULT, null);
            return;
        }

        String itemId = String.valueOf(payload).trim();
        if (itemId.isEmpty() || "null".equalsIgnoreCase(itemId)) {
            sendResponse(out, Command.GET_AUCTION_RESULT, null);
            return;
        }

        clientHandler.setViewingItemId(itemId);
        Auction auction = userService.getAuctionByItemId(itemId);
        sendResponse(out, Command.GET_AUCTION_RESULT, auction);
    }
}
