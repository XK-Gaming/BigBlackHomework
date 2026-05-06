package network;

import service.UserService;
import model.User.User;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

// network/server/ClientHandler.java
/**
 * Worker xử lý một kết nối client.
 *
 * Trách nhiệm class: giữ một Socket, đọc DataPacket trong vòng lặp, chuyển command tới
 * RequestHandler tương ứng, và cung cấp sendPacket để server chủ động push dữ liệu.
 */
public class ClientHandler implements Runnable {
    // Nối ClientHandler với Service
    /** Service nghiệp vụ được các handler của client này dùng chung. */
    private UserService userService = new UserService();
    /** Stream gửi response và gói broadcast về client. */
    private ObjectOutputStream out;
    /** Stream đọc DataPacket request từ client. */
    private ObjectInputStream in;
    /** Socket thuộc quyền xử lý của handler này. */
    private Socket socket;
    /** User đã xác thực trên kết nối này; null trước khi LOGIN thành công. */
    private User user;
    /** Mã item (chuỗi, đồng bộ với khach.status) đang được client xem; null = không trong phiên nào. */
    private volatile String viewingItemId;

    /**
     * Precondition: user là kết quả xác thực của request LOGIN thành công.
     * Postcondition: ClientHandler này được gắn với user truyền vào.
     * Method không trả về giá trị.
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Precondition: itemId là null/rỗng nếu client không xem phiên nào, hoặc là id item trong DB
     * đang hiển thị cho client.
     * Postcondition: viewingItemId được chuẩn hóa bằng trim, hoặc bị xóa về null.
     * Method không trả về giá trị.
     */
    public void setViewingItemId(String itemId) {
        this.viewingItemId = (itemId == null || itemId.isBlank()) ? null : itemId.trim();
    }

    /**
     * Precondition: ClientHandler đã được khởi tạo.
     * Postcondition: Method trả về id item client đang xem, hoặc null.
     */
    public String getViewingItemId() {
        return viewingItemId;
    }

    /**
     * Precondition: ClientHandler đã được khởi tạo.
     * Postcondition: Method trả về user đã xác thực, hoặc null trước khi login.
     */
    public User getUser() {
        return this.user;
    }


    /** Bảng định tuyến command cho kết nối này. */
    private Map<String, RequestHandler> handlers = new HashMap<>();

    /**
     * Precondition: socket là client Socket đã được accept và còn mở.
     * Postcondition: Khởi tạo Object stream và đăng ký các command handler.
     * NOTE: Ném RuntimeException nếu không tạo được stream từ socket.
     */
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

    /**
     * Precondition: userService và ClientHandler hiện tại đã được khởi tạo.
     * Postcondition: handlers chứa toàn bộ command mà protocol server hỗ trợ.
     * Method không trả về giá trị.
     */
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
    /**
     * Precondition: ClientHandler có Object stream đang mở tới client.
     * Postcondition: Method liên tục đọc DataPacket và dispatch sang handler cho đến khi client
     * ngắt kết nối hoặc có lỗi. Stream và socket được đóng trong finally.
     * Method kết thúc khi kết nối kết thúc.
     */
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
    /**
     * Precondition: packet là gói server cần push/response và out còn mở.
     * Postcondition: packet được ghi xuống output stream của client và flush.
     * Method không trả về giá trị.
     * NOTE: IOException được bắt và ghi log để lỗi push không làm sập luồng broadcast.
     */
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
