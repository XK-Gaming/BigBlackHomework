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
        Map<String, String> data = (Map<String, String>) payload;
        String username = data.get("username");

        userService.logout(username);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đăng xuất thành công");

        sendResponse(out, Command.LOGOUT_RESULT, response);
    }
}