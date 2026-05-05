package network;

import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;
import java.util.List;

public class GetAllAuctionsHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    public GetAllAuctionsHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        List<Auction> auctions = userService.getAllAuctions();
        sendResponse(out, "GET_ALL_AUCTIONS_RESULT", auctions);
    }
}