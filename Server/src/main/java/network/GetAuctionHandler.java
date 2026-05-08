package network;

import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;

public class GetAuctionHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    public GetAuctionHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        int itemId = (Integer) payload;

        Auction auction = userService.getAuctionByItemId(String.valueOf(itemId));
        sendResponse(out, Command.GET_AUCTION_RESULT, auction);
    }
}