package network;

import dao.DAOUser;
import model.User.User;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entry point của backend socket server cho hệ thống đấu giá.
 *
 * Trách nhiệm class: mở cổng lắng nghe socket, tạo một ClientHandler cho mỗi kết nối,
 * quản lý danh sách user online, và broadcast cập nhật bid cho các client đang xem cùng item.
 */
public class AuctionServer {
    /** Cổng server, đọc từ server.properties; mặc định là 8080. */
    private static int PORT = 8080;
    /** Danh sách client online, key là username; dùng để push/broadcast có chọn lọc. */
    private static final Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    static {
        Properties props = new Properties();
        try (InputStream input = AuctionServer.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (input != null) {
                props.load(input);
                PORT = Integer.parseInt(props.getProperty("server.port", "8080"));
            }
        } catch (Exception e) {
            System.err.println("Không load được server.properties, dùng port mặc định 8080");
        }
    }

    /**
     * Pool có giới hạn + hàng đợi có bound: tránh {@code newFixedThreadPool} với queue không giới hạn
     * (dễ tràn bộ nhớ khi có quá nhiều kết nối chờ xử lý).
     */
    private final ThreadPoolExecutor threadPool = createWorkerPool();

    // Hàm launch chứa logic lõi của Server
    /**
     * Precondition: PORT đã cấu hình đang trống và process có quyền mở ServerSocket.
     * Postcondition: Server nhận kết nối client liên tục và đưa từng ClientHandler vào thread pool.
     * Nếu khởi động lỗi, lỗi được ghi log và thread pool được shutdown.
     * Method chỉ kết thúc khi server socket dừng do exception hoặc process bị dừng.
     */
    public void launch() {
        // Sử dụng try-with-resources để tự động đóng ServerSocket khi có lỗi
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("=== QUẢN LÝ ĐẤU GIÁ SERVER ===");
            System.out.println("[*] Server đang lắng nghe tại cổng " + PORT + "...");

            // Vòng lặp vô tận để đón khách
            while (true) {
                // accept() sẽ block luồng (chờ đợi) cho đến khi có Client kết nối
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                    clientSocket.setKeepAlive(true); // Giữ cho kết nối không bị Azure tự động ngắt
                    clientSocket.setSoTimeout(0);    // Không bao giờ hết hạn chờ dữ liệu
                    System.out.println("[+] Client mới từ: " + clientSocket.getInetAddress().getHostAddress());

                    // Bọc riêng phần khởi tạo Handler
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    threadPool.execute(clientHandler);

                } catch (Exception e) {
                    // Nếu là lỗi 'invalid stream header', nó sẽ rơi vào đây
                    System.err.println("[-] Bỏ qua một kết nối lỗi: " + e.getMessage());

                    // Đảm bảo đóng socket lỗi để không bị rò rỉ tài nguyên
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        try { clientSocket.close(); } catch (IOException ex) {}
                    }
                    // Vòng lặp vẫn tiếp tục, Server không dừng!
                }// Ném công việc chạy cho threadpool -- giống start thread.
            }
        } catch (IOException e) {
            System.err.println("[-] Lỗi khởi động Server: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }
    /*
      Gửi thông báo tới các client đang xem đúng item (đồng bộ SET_AUCTION / khach.status).
      Không hit DB trong vòng lặp; {@code auctionId} tương ứng id item (numeric) trong mạng của dự án.

      @param auctionId id item đang đấu giá (cùng convention với BidHandler / khach.status)
     * @param command   lệnh (VD: NEW_BID_UPDATE)
     * @param payload   dữ liệu kèm
     */
    /**
     * Precondition: auctionId là id item dạng số theo convention của màn hình đấu giá.
     * Postcondition: Method chuyển auctionId sang String rồi gọi overload còn lại để broadcast.
     * Method không trả về giá trị.
     */
    public static void broadcastToSpecificAuction(long auctionId, String command, Object payload) {
        broadcastToSpecificAuction(Long.toString(auctionId), command, payload);
    }

    /** Broadcast theo {@code itemId} dạng chuỗi (chuẩn dùng khi id không phải số cố định). */
    /**
     * Precondition: itemId xác định item đang được một hoặc nhiều client xem; command là lệnh
     * push mà client biết cách xử lý.
     * Postcondition: Mỗi ClientHandler online có viewingItemId bằng itemId sẽ nhận
     * DataPacket(command, payload). Client đang xem item khác sẽ không nhận.
     * Method không trả về giá trị.
     * NOTE: itemId rỗng hoặc command null thì method dừng ngay và không gửi gì.
     */
    public static void broadcastToSpecificAuction(String itemId, String command, Object payload) {
        if (itemId == null || itemId.isBlank() || command == null) {
            return;
        }
        String target = itemId.trim();
        DataPacket packet = new DataPacket(command, payload);
        for (ClientHandler handler : onlineClients.values()) {
            if (target.equals(handler.getViewingItemId())) {
                handler.sendPacket(packet);
            }
        }
    }
    // Gọi hàm này khi một Client đăng nhập thành công
    /**
     * Precondition: user đã đăng nhập thành công và handler là ClientHandler đang phục vụ user đó.
     * Postcondition: Lưu handler vào onlineClients theo user.username, đồng thời khởi tạo
     * viewingItemId từ cột khach.status trong database.
     * Method không trả về giá trị.
     */
    public static void addOnlineClient(User user, ClientHandler handler) {
        onlineClients.put(user.getUsername(), handler);
        // Đồng bộ trạng thái item đang xem từ DB khi user vừa online.
        String viewingItemId = DAOUser.getInstance().Get_Status(user.getUsername());
        handler.setViewingItemId(viewingItemId);
    }

    // Gọi hàm này khi Client ngắt kết nối
    /**
     * Precondition: username là tài khoản có thể đang tồn tại trong onlineClients.
     * Postcondition: Nếu có ClientHandler đăng ký dưới username đó thì xóa khỏi onlineClients.
     * Method không trả về giá trị.
     * NOTE: Gọi với username không online thì không có thay đổi nào.
     */
    public static void removeOnlineClient(String username) {
        onlineClients.remove(username);
    }

    // ĐÂY LÀ HÀM TÌM NHANH MÀ BẠN CẦN
    /**
     * Precondition: username là key đăng nhập dùng trong onlineClients.
     * Postcondition: Method trả về ClientHandler đang hoạt động của username, hoặc null nếu
     * user hiện không online.
     */
    public static ClientHandler getHandlerByUsername(String username) {
        return onlineClients.get(username);
    }

    /**
     * Precondition: Runtime có thể đọc số processor hiện có.
     * Postcondition: Method trả về ThreadPoolExecutor có giới hạn để chạy ClientHandler.
     * NOTE: CallerRunsPolicy khiến thread accept tự chạy task khi queue đầy, giúp giảm tốc nhận
     * kết nối thay vì để queue tăng không giới hạn.
     */
    private static ThreadPoolExecutor createWorkerPool() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int coreSize = Math.min(32, cores * 2);
        int maxSize = Math.min(100, Math.max(coreSize, cores * 4));
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(512);
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "auction-server-worker-" + seq.incrementAndGet());
                t.setDaemon(false);
                return t;
            }
        };
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                queue,
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    // Hàm main bây giờ cực kỳ gọn gàng
    /**
     * Precondition: Ứng dụng được chạy như process server.
     * Postcondition: Tạo AuctionServer và gọi launch() để bắt đầu lắng nghe client.
     * Method chỉ kết thúc khi launch() kết thúc.
     */
    public static void main(String[] args) {
        AuctionServer server = new AuctionServer();
        server.launch(); // Gọi hàm launch tại đây
    }
}
