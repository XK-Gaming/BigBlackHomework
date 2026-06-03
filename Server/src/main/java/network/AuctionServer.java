package network;

import dao.DAOUser;
import model.User.User;
import org.checkerframework.checker.nullness.qual.NonNull;
import service.AuctionEngine;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// Socket server.
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
            System.err.println("[AuctionServer] Không tìm thấy hoặc lỗi đọc file server.properties, dùng port mặc định 8080");
        }
    }

    private final ThreadPoolExecutor threadPool = createWorkerPool();
    private AuctionEngine auctionEngine;
    // Khởi chạy server.
    public void launch() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("=== QUẢN LÝ ĐẤU GIÁ SERVER ===");

            auctionEngine = new AuctionEngine();
            auctionEngine.startEngine();
            System.out.println("[AuctionServer] Background Engine đã khởi động, quét trạng thái mỗi 5 giây.");

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    threadPool.execute(clientHandler);
                } catch (IOException e) {
                    System.err.println("[AuctionServer] Lỗi tiếp nhận kết nối Client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (auctionEngine != null) {
                auctionEngine.close();
                System.out.println("[AuctionServer] Background Engine đã đóng an toàn.");
            }
            threadPool.shutdown();
        }
    }
    // Broadcast tới client.
    public static void broadcastToSpecificAuction(String itemId, Command command, Object payload) {
        if (command == null) return;

        DataPacket packet = new DataPacket(command, payload);

        if (itemId == null || itemId.isBlank()) {
            for (ClientHandler handler : onlineClients.values()) {
                handler.sendPacket(packet);
            }
            return;
        }

        String target = itemId.trim();
        for (ClientHandler handler : onlineClients.values()) {
            if (target.equals(handler.getViewingItemId())) {
                handler.sendPacket(packet);
            }
        }
    }
    // Cập nhật client online.
    public static void addOnlineClient(User user, ClientHandler handler) {
        String username = user.getUsername();
        onlineClients.put(username, handler);

        CompletableFuture.runAsync(() -> {
            try {
                String viewingItemId = DAOUser.getInstance().Get_Status(username);

                ClientHandler currentActiveHandler = onlineClients.get(username);
                if (currentActiveHandler == handler) {
                    handler.setViewingItemId(viewingItemId);
                }
            } catch (Exception e) {
                System.err.println("[AuctionServer] Lỗi lấy trạng thái viewingItem từ DB cho user " + username + ": " + e.getMessage());
            }
        });
    }
    // Cập nhật client online.
    public static void removeOnlineClient(String username) {
        if (username == null) return;

        try {
            AutoBidManager.getInstance().disableAllForUser(username, "logout/disconnect");
        } catch (Exception e) {
            System.err.println("[AuctionServer] Lỗi khi dừng AutoBid cho user " + username + ": " + e.getMessage());
        }

        onlineClients.remove(username);
    }

    public static ClientHandler getHandlerByUsername(String username) {
        if (username == null) return null;
        return onlineClients.get(username);
    }

    public static boolean isUserOnline(String username) {
        if (username == null) return false;
        return onlineClients.containsKey(username);
    }
    // Gửi dữ liệu tới user.
    public static void sendToSpecificUser(String username, Command command, Object payload) {
        ClientHandler handler = getHandlerByUsername(username);
        if (handler != null) {
            handler.sendPacket(new DataPacket(command, payload));
        } else {
            System.out.println("User " + username + " không online, bỏ qua gửi thông báo trực tiếp.");
        }
    }
    // Tạo dữ liệu.
    private static ThreadPoolExecutor createWorkerPool() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int coreSize = Math.min(32, cores * 2);
        int maxSize = Math.min(100, Math.max(coreSize, cores * 4));
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(512);

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            // Tạo thread nền.
            @Override
            public Thread newThread(@NonNull Runnable r) {
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

    public static void main(String[] args) {
        AuctionServer server = new AuctionServer();
        server.launch();
    }
}
