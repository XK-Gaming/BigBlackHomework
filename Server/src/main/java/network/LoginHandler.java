package network;

import service.UserService;
import model.User.User;
import network.Command;
import network.DataPacket;
// import server.AuctionServer; // Import lớp quản lý Server của bạn

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class LoginHandler extends BaseHandler implements RequestHandler {
    private UserService userService;
    private ClientHandler clientHandler; // Thêm biến này để biết Handler nào đang xử lý

    // Cập nhật Constructor để nhận ClientHandler
    public LoginHandler(UserService userService, ClientHandler clientHandler) {
        this.userService = userService;
        this.clientHandler = clientHandler;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, String> loginInfo = (Map<String, String>) payload;
        Map<String, Object> response = new HashMap<>();
        User user = userService.loginAndGetUser(loginInfo.get("username"), loginInfo.get("password"));
        if (user != null) {
            // 1. Gắn đối tượng User vào Handler hiện tại
            clientHandler.setUser(user);

            // 2. Đăng ký Handler này vào danh sách Online tập trung của Server
            // Giả sử bạn có lớp AuctionServer quản lý Map<Integer, ClientHandler>
            AuctionServer.addOnlineClient(user, clientHandler);

            response.put("success", true);
            response.put("user", user);
        } else {
            response.put("success", false);}

        sendResponse(out, Command.LOGIN_RESULT, response);
    }
}
