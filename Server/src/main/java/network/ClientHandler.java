package network;

import service.UserService;
import model.User.User;

import java.io.*;
import java.net.Socket;
import java.util.EnumMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final UserService userService = new UserService();
    private final Socket socket;

    // Sử dụng volatile đảm bảo cập nhật luồng an toàn giữa các Thread
    private volatile ObjectOutputStream out;
    private volatile ObjectInputStream in;
    private volatile User user;
    private volatile String viewingItemId;

    private final Map<Command, RequestHandler> handlers = new EnumMap<>(Command.class);
    private final Object streamLock = new Object();

    public void setUser(User user) { this.user = user; }
    public User getUser() { return this.user; }
    public void setViewingItemId(String itemId) { this.viewingItemId = (itemId == null || itemId.isBlank()) ? null : itemId.trim(); }
    public String getViewingItemId() { return viewingItemId; }

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush();
            this.in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khởi tạo luồng I/O: " + e.getMessage(), e);
        }
        initHandlers();
    }

    public void initHandlers() {
        handlers.put(Command.LOGIN, new LoginHandler(this.userService, this));
        handlers.put(Command.REGISTER, new RegisterHandler(this.userService));
        handlers.put(Command.CREATE_ITEM, new Creater_ItemHandler(this.userService));
        handlers.put(Command.SELECT_ITEMS, new Select_Items(this.userService));
        handlers.put(Command.GET_AUCTION, new GetAuctionHandler(this.userService, this));
        handlers.put(Command.SET_AUCTION, new SetAuctionHandler(this.userService, this));
        handlers.put(Command.BID, new BidHandler(this.userService));
        handlers.put(Command.SET_AUTO_BID, new AutoBidHandler(this));
        handlers.put(Command.GET_ALL_AUCTIONS, new GetAllAuctionsHandler(this.userService));
        handlers.put(Command.UPDATE_USER, new UpdateUserHandler(this.userService));
        handlers.put(Command.CHANGE_PASSWORD, new ChangePasswordHandler(this.userService));
        handlers.put(Command.LOGOUT, new LogoutHandler(this.userService));
        handlers.put(Command.EDIT_ITEM, new EditItemHandler(this.userService));
        handlers.put(Command.DELETE_ITEM, new DeleteItemHandler());
        handlers.put(Command.GET_SELLER_ITEMS, new GetSellerItemsHandler());
        handlers.put(Command.GET_BIDDER_HISTORY, new GetBidderHistoryHandler(this.userService));
        handlers.put(Command.SET_ALLOW, new SetAllowHandler(this.userService));
        handlers.put(Command.RECHARGE_AMOUNT, new RechargeAmountHandler(this.userService));
        handlers.put(Command.GET_PENDING_DEPOSITS, new DepositManagementHandler.GetPendingHandler(this.userService));
        handlers.put(Command.APPROVE_DEPOSIT, new DepositManagementHandler.ApproveHandler(this.userService));
        handlers.put(Command.REJECT_DEPOSIT, new DepositManagementHandler.RejectHandler(this.userService));
        handlers.put(Command.DELETE_DEPOSIT_HISTORY, new DepositManagementHandler.DeleteHistoryHandler(this.userService));
        handlers.put(Command.GET_USER_INFO, new GetUserInfoHandler(this.userService));
        handlers.put(Command.BIDDER_PAY, new PaymentHandler(this.userService));
        handlers.put(Command.GET_ALL_USERS, new GetAllUsersHandler(this.userService));
        handlers.put(Command.DELETE_USER, new DeleteUserHandler(this.userService));
    }

    @Override
    public void run() {
        String loggedInUsername = null;
        try {
            while (true) {
                if (in == null) break;
                Object obj = in.readObject();
                if (!(obj instanceof DataPacket request)) continue;

                if (this.user != null) {
                    loggedInUsername = this.user.getUsername();
                }

                Command command = request.command();
                RequestHandler handler = handlers.get(command);
                if (handler != null) {
                    try {
                        handler.handle(request.payload(), out);
                    } catch (Exception e) {
                        System.err.println("Lỗi logic xử lý lệnh " + command + ": " + e.getMessage());
                    }
                }
            }
        } catch (EOFException e) {
            System.out.println("Một Client đã ngắt kết nối chủ động (EOF).");
        } catch (Exception e) {
            System.err.println("[Server] Kết nối bị ngắt do lệnh đá tài khoản hoặc mất tín hiệu mạng.");
        } finally {
            if (loggedInUsername == null && this.user != null) {
                loggedInUsername = this.user.getUsername();
            }
            cleanup(loggedInUsername);
        }
    }

    private void cleanup(String usernameToDisconnect) {
        synchronized (streamLock) {
            try {
                if (usernameToDisconnect != null) {
                    System.out.println("[Server Cleanup] Đang xóa tên khỏi danh sách Online: " + usernameToDisconnect);
                    AuctionServer.removeOnlineClient(usernameToDisconnect);
                } else if (user != null) {
                    AuctionServer.removeOnlineClient(user.getUsername());
                }

                // Đóng an toàn các kết nối vật lý và gán null ngay lập tức
                if (in != null) { try { in.close(); } catch (IOException ignored) {} in = null; }
                if (out != null) { try { out.close(); } catch (IOException ignored) {} out = null; }
                if (socket != null && !socket.isClosed()) { try { socket.close(); } catch (IOException ignored) {} }

                System.out.println("Đã giải phóng hoàn toàn tài nguyên Socket phía Server.");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void sendPacket(DataPacket packet) {
        synchronized (streamLock) {
            try {
                if (out != null && socket != null && !socket.isClosed()) {
                    out.reset();
                    out.writeObject(packet);
                    out.flush();
                }
            } catch (IOException e) {
                System.err.println("[ClientHandler] Không thể gửi gói tin, kết nối đã bị đóng trước đó.");
            }
        }
    }

    public void forceClose() {
        synchronized (streamLock) {
            try {
                if (socket != null && !socket.isClosed()) {
                    System.out.println("[Server SSO] Đang ép đóng kết nối socket của user: "
                            + (user != null ? user.getUsername() : "Chưa đăng nhập"));
                    socket.close(); // Đóng socket ép luồng in.readObject() dừng lại
                }
            } catch (IOException e) {
                System.err.println("[ClientHandler] Lỗi khi ép đóng socket: " + e.getMessage());
            }
        }
    }
}