package network;

import service.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý request LOGOUT.
 *
 * Payload mong đợi: username dạng String.
 */
public class LogoutHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    /**
     * Precondition: userService đã được khởi tạo.
     * Postcondition: Handler có thể chuyển xử lý logout xuống service layer.
     */
    public LogoutHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    /**
     * Precondition: payload chứa username cần logout; out còn mở.
     * Postcondition: Gửi LOGOUT_RESULT với success=true.
     * Method không trả về giá trị.
     * NOTE: UserService.logout() hiện đang rỗng, nên chưa xóa user khỏi onlineClients.
     */
    public void handle(Object payload, ObjectOutputStream out) {
        String payloadJson = gson.toJson(payload);
        String username = gson.fromJson(payloadJson, String.class);

        userService.logout(username);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đăng xuất thành công");

        sendResponse(out, "LOGOUT_RESULT", response);
    }
}
