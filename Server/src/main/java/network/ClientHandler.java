package network;

import service.UserService;
import model.User.User;

import java.io.*;
import java.net.Socket;
import java.util.EnumMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final UserService userService = new UserService();
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final Socket socket;
    private User user;

    // Khởi tạo user, ViewingItemid cho từng Clienthandler
    private volatile String viewingItemId;
    public void setUser(User user) {
        this.user = user;
    }
    public void setViewingItemId(String itemId) {this.viewingItemId = (itemId == null || itemId.isBlank()) ? null : itemId.trim();}
    public String getViewingItemId() {return viewingItemId;}
    public User getUser() {
        return this.user;
    }


    // Tạo kết nối
    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush();
            this.in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {throw new RuntimeException("Lỗi khởi tạo luồng I/O: " + e.getMessage(), e);}
        initHandlers();
    }



    // Sử dụng EnumMap để ánh xạ trực tiếp Command -> Handler
    // Tránh phải if-else hoặc switch-case dài
    // initHandlers có vai trò để đăng ký tất cả các handler vào map ngay
    // khi khởi tạo ClientHandler... Và run() sẽ có trách nhiệm lấy về handler
    // tương ứng để xử lý request. Sau đó chạy triển khai interface RequestHandler của handler đó.


    private final Map<Command, RequestHandler> handlers = new EnumMap<>(Command.class);

    public void initHandlers() {
        handlers.put(Command.LOGIN, new LoginHandler(this.userService, this));
        handlers.put(Command.REGISTER, new RegisterHandler(this.userService));
        handlers.put(Command.CREATE_ITEM, new Creater_ItemHandler(this.userService));
        handlers.put(Command.SELECT_ITEMS, new Select_Items(this.userService));
        handlers.put(Command.GET_AUCTION, new GetAuctionHandler(this.userService, this));
        handlers.put(Command.SET_AUCTION, new SetAuctionHandler(this.userService, this));
        handlers.put(Command.BID, new BidHandler(this.userService));
        handlers.put(Command.GET_ALL_AUCTIONS, new GetAllAuctionsHandler(this.userService));
        handlers.put(Command.UPDATE_USER, new UpdateUserHandler(this.userService));
        handlers.put(Command.CHANGE_PASSWORD, new ChangePasswordHandler(this.userService));
        handlers.put(Command.LOGOUT, new LogoutHandler(this.userService));
        handlers.put(Command.EDIT_ITEM, new EditItemHandler(this.userService));
        handlers.put(Command.DELETE_ITEM, new DeleteItemHandler());
        handlers.put(Command.GET_SELLER_ITEMS,new GetSellerItemsHandler(this.userService));
        handlers.put(Command.GET_BIDDER_HISTORY, new GetBidderHistoryHandler(this.userService));
        handlers.put(Command.SET_ALLOW,new SetAllowHandler(this.userService));
        handlers.put(Command.DELETE_ITEM,new DeleteItems(this.userService));
        handlers.put(Command.RECHARGE_AMOUNT,new RechargeAmountHandler(this.userService));
    }
    @Override
    public void run() {
        try {
            while (true) {
                // Đọc đối tượng từ luồng
                Object obj = in.readObject();
                if (!(obj instanceof DataPacket request)) continue;

                Command command = request.command(); // Giả sử trả về Enum Command

                // Tìm bộ xử lý trực tiếp bằng Enum Key
                RequestHandler handler = handlers.get(command);
                if (handler != null) {
                    try {
                        handler.handle(request.payload(), out);
                    } catch (Exception e) {
                        System.err.println("Lỗi logic xử lý lệnh " + command + ": " + e.getMessage());
                        e.printStackTrace();
                        // Bạn có thể gửi một gói tin lỗi về Client tại đây nếu cần thiết
                    }}
            }
        }
        catch (EOFException e) {System.out.println("Một Client đã ngắt kết nối (EOF).");}
        catch (ClassNotFoundException e) {System.err.println("Lỗi: Client gửi đối tượng không xác định: " + e.getMessage());}
        catch (Exception e) {System.err.println("Mất kết nối đột ngột: " + e.getMessage());} finally {cleanup();}
    }

    private void cleanup() {
        try {
            if (user != null) {
                AuctionServer.removeOnlineClient(user.getUsername());
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("Đã dọn dẹp tài nguyên Socket.");
        } catch (IOException ex) {ex.printStackTrace();}
    }
    public void sendPacket(DataPacket packet) {
        try {
            synchronized (out) { // Đảm bảo không bị xung đột khi nhiều luồng cùng gửi
                out.reset();
                out.writeObject(packet);
                out.flush();
            }
        } catch (IOException e) {e.printStackTrace();}
    }
}