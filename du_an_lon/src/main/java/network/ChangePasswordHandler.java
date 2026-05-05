package network;

import Service_.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class ChangePasswordHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    public ChangePasswordHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        String payloadJson = gson.toJson(payload);
        Map<String, String> data = gson.fromJson(payloadJson, Map.class);

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

        sendResponse(out, "CHANGE_PASSWORD_RESULT", response);
    }
}