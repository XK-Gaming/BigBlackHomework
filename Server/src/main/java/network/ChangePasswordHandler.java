package network;

import service.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý request CHANGE_PASSWORD.
 *
 * Payload mong đợi: Map chứa username, oldPassword và newPassword.
 */
public class ChangePasswordHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    /**
     * Precondition: userService đã được khởi tạo.
     * Postcondition: Handler có thể chuyển xử lý đổi mật khẩu xuống service layer.
     */
    public ChangePasswordHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    /**
     * Precondition: payload chứa username, oldPassword và newPassword; out còn mở.
     * Postcondition: Gửi CHANGE_PASSWORD_RESULT với success flag và message.
     * Method không trả về giá trị.
     * NOTE: oldPassword phải trùng mật khẩu hiện tại thì mới thành công.
     */
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
