package network;

import service.UserService;

import java.io.ObjectOutputStream;
import java.util.Map;

public class RechargeAmountHandler  extends BaseHandler implements RequestHandler{
    private UserService userService;
    public RechargeAmountHandler(UserService userService) { this.userService = userService;};

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> Info = (Map<String, Object>) payload;
        String username = (String) Info.get("username");
        double amount = (double) Info.get("amount");
        boolean result = userService.rechargeAmount(username, amount);
        sendResponse(out, Command.RECHARGE_AMOUNT_RESULT, result);
    }

    }

