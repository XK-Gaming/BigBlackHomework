package network;
import network.Command;
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
    // Biến online sử dụng ConcurrentHashMap để đảm bảo thread-safe
    // khi nhiều ClientHandler cùng truy cập và sửa đổi.
    private static final Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
    static {
        Properties props = new Properties();
        try (InputStream input = AuctionServer.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (input != null) {
                props.load(input);
                PORT = Integer.parseInt(props.getProperty("server.port", "8080"));}
        } catch (Exception e) {e.printStackTrace();}
    }

    /**
     * Pool có giới hạn + hàng đợi có bound: tránh {@code newFixedThreadPool} với queue không giới hạn
     * (dễ tràn bộ nhớ khi có quá nhiều kết nối chờ xử lý).
     */
    private final ThreadPoolExecutor threadPool = createWorkerPool();
    public void launch() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("=== QUẢN LÝ ĐẤU GIÁ SERVER ===");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                threadPool.execute(clientHandler); // Ném công việc chạy cho threadpool -- giống start thread.
            }
        }
        catch (IOException e) {e.printStackTrace();}
        finally {threadPool.shutdown();}
    }


    /** Broadcast theo {@code itemId} dạng chuỗi (chuẩn dùng khi id không phải số cố định). */
    public static void broadcastToSpecificAuction(String itemId, Command command, Object payload) {
        if (command == null) return; // Nếu lệnh null thì không làm gì cả

        DataPacket packet = new DataPacket(command, payload);

        // TRƯỜNG HỢP 1: Gửi cho những người KHÔNG xem item nào (itemId truyền vào trống/null)
        if (itemId == null || itemId.isBlank()) {
            for (ClientHandler handler : onlineClients.values()) {
                // Không lọc viewingItemId nữa, cứ online là gửi hết!
                    handler.sendPacket(packet);
            }
            return; // Sau khi chạy hết vòng lặp gửi cho mọi người mới return
        }

        // TRƯỜNG HỢP 2: Gửi đích danh cho những người ĐANG XEM item được chỉ định
        String target = itemId.trim();
        for (ClientHandler handler : onlineClients.values()) {
            // Sử dụng Objects.equals hoặc so sánh chuỗi an toàn bằng cách đẩy biến chắc chắn khác null lên trước
            if (target.equals(handler.getViewingItemId())) {
                handler.sendPacket(packet);
            }
        }
    }
    // Mỗi client khi tạo kết nối sẽ tạo một ClientHandler riêng,
    // và ClientHandler đó sẽ quản lý luồng giao tiếp với client đó.
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
    public static void sendToSpecificUser (String username, Command command, Object payload) {
        ClientHandler handler = getHandlerByUsername(username);
        if (handler != null) {
            // Gọi hàm gửi dữ liệu của chính handler đó
            // Bạn có thể dùng hàm sendResponse dùng chung từ BaseHandler nếu có truyền 'out' của handler vào
            handler.sendPacket(new DataPacket(command, payload));
        } else {
            System.out.println("User " + username + " không online, bỏ qua gửi thông báo trực tiếp.");
        }
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
        server.launch();// Gọi hàm launch tại đây
    }
}