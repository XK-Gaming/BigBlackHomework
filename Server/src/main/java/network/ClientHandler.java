package network;

import service.UserService;
import model.User.User;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

// network/server/ClientHandler.java
public class ClientHandler implements Runnable {
    // Nối ClientHandler với Service
    private UserService userService = new UserService();
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Socket socket;
    private User user;
    /** Mã item (chuỗi, đồng bộ với khach.status) đang được client xem; null = không trong phiên nào. */
    private volatile String viewingItemId;

    public void setUser(User user) {
        this.user = user;
    }

    public void setViewingItemId(String itemId) {
        this.viewingItemId = (itemId == null || itemId.isBlank()) ? null : itemId.trim();
    }

    public String getViewingItemId() {
        return viewingItemId;
    }

    public User getUser() {
        return this.user;
    }


    private Map<String, RequestHandler> handlers = new HashMap<>();

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            // QUAN TRỌNG: Khởi tạo Output và flush trước khi khởi tạo Input
            // Nếu không flush(), Server và Client sẽ chờ nhau mãi mãi -> Treo
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush();

            this.in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khởi tạo luồng I/O: " + e.getMessage(), e);
        }
        initHandlers();
    }

    public void initHandlers() {
        handlers.put("LOGIN", new LoginHandler(this.userService,this));
        handlers.put("REGISTER", new RegisterHandler(this.userService));
        handlers.put("CREATE_ITEM", new Creater_ItemHandler(this.userService));
        handlers.put("SELECT_ITEMS", new Select_Items(this.userService));
        handlers.put("GET_AUCTION", new GetAuctionHandler(this.userService));
        handlers.put("SET_AUCTION", new SetAuctionHandler(this.userService, this));
        handlers.put("BID", new BidHandler(this.userService));
        handlers.put("GET_ALL_AUCTIONS", new GetAllAuctionsHandler(this.userService));
        handlers.put("UPDATE_USER", new UpdateUserHandler(this.userService));
        handlers.put("CHANGE_PASSWORD", new ChangePasswordHandler(this.userService));
        handlers.put("LOGOUT", new LogoutHandler(this.userService));
    }

    @Override
    public void run() {
        try {
            // Lặp vô tận để đọc đối tượng từ luồng
            while (true) {
                // Bắt thẳng đối tượng DataPacket
                DataPacket request = (DataPacket) in.readObject();
                String command = request.getCommand();

                System.out.println("SERVER NHẬN ĐƯỢC LỆNH: " + command);
                // Tìm bộ xử lý tương ứng trong Map
                RequestHandler handler = handlers.get(command);

                if (handler != null) {
                    // Chuyển việc xử lý và trả lời (out) cho Handler tương ứng
                    handler.handle(request.getPayload(), out);
                } else {
                    System.out.println("Lệnh không hợp lệ: " + command);
                }
            }
        } catch (EOFException e) {
            // Bắt lỗi EOFException khi Client chủ động ngắt kết nối hoặc tắt App
            System.out.println("Một Client đã ngắt kết nối an toàn.");
        } catch (Exception e) {
            System.out.println("Mất kết nối với Client đột ngột: " + e.getMessage());
        } finally {
            // LUÔN LUÔN dọn dẹp tài nguyên khi luồng kết thúc để Server không bị sập
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ex) {
                System.out.println("Lỗi khi đóng Socket: " + ex.getMessage());
            }
        }
    }
    // Thêm vào bên trong lớp ClientHandler
    public void sendPacket(DataPacket packet) {
        try {
            synchronized (out) { // Đảm bảo không bị xung đột khi nhiều luồng cùng gửi
                out.writeObject(packet);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Lỗi gửi gói tin tới " + (user != null ? user.getUsername() : "Unknown") + ": " + e.getMessage());
        }
    }
}