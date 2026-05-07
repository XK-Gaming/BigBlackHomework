package network;

import model.User.Bidder;
import service.UserService;

import java.io.ObjectOutputStream;
import java.util.Map;

public class BidderPaymentAccountHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    public BidderPaymentAccountHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map info = (Map) payload;
        String username =(String) info.get("username");
        Double money = (Double) info.get("money");
        userService.PaymentAccount(username,money);
        sendResponse(out, "BIDDER_PAYMENT_ACCOUNT_RESULT", "");
    }
}
