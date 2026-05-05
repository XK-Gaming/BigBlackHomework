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

public class AuctionServer {
    private static int PORT = 8080;
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
    public void launch() {
        // Sử dụng try-with-resources để tự động đóng ServerSocket khi có lỗi
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("=== QUẢN LÝ ĐẤU GIÁ SERVER ===");
            System.out.println("[*] Server đang lắng nghe tại cổng " + PORT + "...");

            // Vòng lặp vô tận để đón khách
            while (true) {
                // accept() sẽ block luồng (chờ đợi) cho đến khi có Client kết nối
                Socket clientSocket = serverSocket.accept();
                System.out.println("[+] Client mới kết nối từ IP: " + clientSocket.getInetAddress().getHostAddress());

                // Khởi tạo ClientHandler và ném vào ThreadPool xử lý
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                threadPool.execute(clientHandler); // Ném công việc chạy cho threadpool -- giống start thread.
            }
        } catch (IOException e) {
            System.err.println("[-] Lỗi khởi động Server: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }
    /**
     * Gửi thông báo tới các client đang xem đúng item (đồng bộ SET_AUCTION / khach.status).
     * Không hit DB trong vòng lặp; {@code auctionId} tương ứng id item (numeric) trong mạng của dự án.
     *
     * @param auctionId id item đang đấu giá (cùng convention với BidHandler / khach.status)
     * @param command   lệnh (VD: NEW_BID_UPDATE)
     * @param payload   dữ liệu kèm
     */
    public static void broadcastToSpecificAuction(long auctionId, String command, Object payload) {
        broadcastToSpecificAuction(Long.toString(auctionId), command, payload);
    }

    /** Broadcast theo {@code itemId} dạng chuỗi (chuẩn dùng khi id không phải số cố định). */
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
    public static void addOnlineClient(User user, ClientHandler handler) {
        onlineClients.put(user.getUsername(), handler);
        // Đồng bộ trạng thái item đang xem từ DB khi user vừa online.
        String viewingItemId = DAOUser.getInstance().Get_Status(user.getUsername());
        handler.setViewingItemId(viewingItemId);
    }

    // Gọi hàm này khi Client ngắt kết nối
    public static void removeOnlineClient(String username) {
        onlineClients.remove(username);
    }

    // ĐÂY LÀ HÀM TÌM NHANH MÀ BẠN CẦN
    public static ClientHandler getHandlerByUsername(String username) {
        return onlineClients.get(username);
    }

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
    public static void main(String[] args) {
        AuctionServer server = new AuctionServer();
        server.launch(); // Gọi hàm launch tại đây
    }
}