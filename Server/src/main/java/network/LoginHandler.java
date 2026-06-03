package network;

import service.UserService;
import model.User.User;
import model.exception.UnauthorizedException;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

// Request đăng nhập.
public class LoginHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;
    private final ClientHandler clientHandler;

    public LoginHandler(UserService userService, ClientHandler clientHandler) {
        this.userService = userService;
        this.clientHandler = clientHandler;
    }

    // Xử lý request đăng nhập.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        @SuppressWarnings("unchecked")
        Map<String, String> loginInfo = (Map<String, String>) payload;
        Map<String, Object> response = new HashMap<>();

        try {
            String username = loginInfo.get("username");
            String password = loginInfo.get("password");

            User user = userService.loginAndGetUser(username, password);

            ClientHandler oldClient = AuctionServer.getHandlerByUsername(username);
            if (oldClient != null && oldClient != this.clientHandler) {
                System.out.println("[SSO] Phát hiện tài khoản '" + username + "' đăng nhập từ vị trí mới. Tiến hành đá máy cũ...");

                Map<String, Object> kickPayload = new HashMap<>();
                kickPayload.put("reason", "Tài khoản của bạn đã được đăng nhập từ một thiết bị khác.");

                oldClient.sendPacket(new DataPacket(Command.FORCE_LOGOUT, kickPayload));

                oldClient.forceClose();

                AuctionServer.removeOnlineClient(username);
            }

            clientHandler.setUser(user);
            AuctionServer.addOnlineClient(user, clientHandler);

            response.put("success", true);
            response.put("user", user);

        } catch (UnauthorizedException e) {
            fillErrorResponse(response, e);
        }

        sendResponse(out, Command.LOGIN_RESULT, response);
    }
}
