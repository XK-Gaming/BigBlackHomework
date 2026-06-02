package network;

import service.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class LogoutHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public LogoutHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        String username = extractUsername(payload);

        // Logout: gọi removeOnlineClient để vừa xóa online user vừa tắt AutoBid đang chạy.
        userService.logout(username);
        AuctionServer.removeOnlineClient(username);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đăng xuất thành công");

        sendResponse(out, Command.LOGOUT_RESULT, response);
    }

    private String extractUsername(Object payload) {
        if (payload instanceof Map<?, ?> data) {
            Object username = data.get("username");
            return username == null ? "" : String.valueOf(username);
        }
        return payload == null ? "" : String.valueOf(payload);
    }
}
