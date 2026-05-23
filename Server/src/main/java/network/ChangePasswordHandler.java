package network;

import service.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class ChangePasswordHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public ChangePasswordHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, String> data = (Map<String, String>) payload;

        String username = data.get("username");
        String oldPassword = data.get("oldPassword");
        String newPassword = data.get("newPassword");

        Map<String, Object> response = new HashMap<>();

        boolean success = userService.changePassword(username, oldPassword, newPassword);

        if (success) {
            response.put("success", true);
            response.put("message", "Đổi mật khẩu thành công");
        } else {
            response.put("success", false);
            response.put("message", "Mật khẩu cũ không đúng");
        }

        sendResponse(out, Command.CHANGE_PASSWORD_RESULT, response);
    }
}