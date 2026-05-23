package network;

import service.UserService;
import model.User.User;
import model.exception.UnauthorizedException;
// import server.AuctionServer; // Import lớp quản lý Server của bạn

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class LoginHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;
    private final ClientHandler clientHandler; // Thêm biến này để biết Handler nào đang xử lý

    // Cập nhật Constructor để nhận ClientHandler
    public LoginHandler(UserService userService, ClientHandler clientHandler) {
        this.userService = userService;
        this.clientHandler = clientHandler;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, String> loginInfo = (Map<String, String>) payload;
        Map<String, Object> response = new HashMap<>();
        try {
            User user = userService.loginAndGetUser(loginInfo.get("username"), loginInfo.get("password"));
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
