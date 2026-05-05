package network;

import Service_.UserService;
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
        String itemId_Str = String.valueOf(itemId);

        Auction auction = userService.getAuctionByItemId(itemId_Str);
        sendResponse(out, "GET_AUCTION_RESULT", auction);
    }
}