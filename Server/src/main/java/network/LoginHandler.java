package network;

import service.UserService;
import model.User.User;
import model.exception.UnauthorizedException;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class LoginHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;
    private final ClientHandler clientHandler;

    public LoginHandler(UserService userService, ClientHandler clientHandler) {
        this.userService = userService;
        this.clientHandler = clientHandler;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        @SuppressWarnings("unchecked")
        Map<String, String> loginInfo = (Map<String, String>) payload;
        Map<String, Object> response = new HashMap<>();

        try {
            String username = loginInfo.get("username");
            String password = loginInfo.get("password");

            // 1. Xác thực tài khoản, mật khẩu từ Database
            User user = userService.loginAndGetUser(username, password);

            // 2. Xử lý Single Sign-On (SSO): Kiểm tra xem tài khoản này có đang online ở máy khác không
            ClientHandler oldClient = AuctionServer.getHandlerByUsername(username);
            if (oldClient != null && oldClient != this.clientHandler) {
                System.out.println("[SSO] Phát hiện tài khoản '" + username + "' đăng nhập từ vị trí mới. Tiến hành đá máy cũ...");

                Map<String, Object> kickPayload = new HashMap<>();
                kickPayload.put("reason", "Tài khoản của bạn đã được đăng nhập từ một thiết bị khác.");

                // Gửi lệnh đá sang máy cũ
                oldClient.sendPacket(new DataPacket(Command.FORCE_LOGOUT, kickPayload));

                // Ép đóng socket máy cũ ngay lập tức (Kích hoạt ngoại lệ bên thread run() của máy cũ)
                oldClient.forceClose();

                // Chủ động xóa luôn máy cũ ra khỏi danh sách Online để giải phóng slot
                AuctionServer.removeOnlineClient(username);
            }

            // 3. Đăng ký thông tin máy mới vào hệ thống
            clientHandler.setUser(user);
            AuctionServer.addOnlineClient(user, clientHandler);

            response.put("success", true);
            response.put("user", user);

        } catch (UnauthorizedException e) {
            fillErrorResponse(response, e);
        }

        // Gửi kết quả đăng nhập về cho chính máy này
        sendResponse(out, Command.LOGIN_RESULT, response);
    }
}