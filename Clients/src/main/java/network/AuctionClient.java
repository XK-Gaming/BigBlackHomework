package network;

import javafx.application.Platform;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionClient {
    private volatile Socket socket;
    private volatile ObjectOutputStream out;
    private volatile ObjectInputStream in;

    private final Object writeLock = new Object();
    private final Object connectionLock = new Object();

    // 🌟 SỬA: Thay đổi từ 1 biến đơn lẻ thành Danh sách Listener an toàn luồng
    private final List<ServerListener> listeners = new CopyOnWriteArrayList<>();

    private static volatile AuctionClient instance;

    private AuctionClient() {}

    public static AuctionClient getInstance() {
        if (instance == null) {
            synchronized (AuctionClient.class) {
                if (instance == null) {
                    instance = new AuctionClient();
                }
            }
        }
        return instance;
    }

    // 🌟 SỬA: Thay thế hàm setListener cũ thành cơ chế ĐĂNG KÝ (Thêm vào danh sách)
    public void addListener(ServerListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            System.out.println("[AuctionClient] Đã thêm bộ lắng nghe: " + listener.getClass().getSimpleName() + " (Tổng số: " + listeners.size() + ")");
        }
    }

    // 🌟 BỔ SUNG: Hàm HỦY ĐĂNG KÝ khi một màn hình bị đóng lại để giải phóng bộ nhớ
    public void removeListener(ServerListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            System.out.println("[AuctionClient] Đã gỡ bỏ bộ lắng nghe: " + listener.getClass().getSimpleName() + " (Còn lại: " + listeners.size() + ")");
        }
    }

    public void connect(String serverIp, int serverPort) {
        synchronized (connectionLock) {
            if (socket != null && !socket.isClosed()) {
                System.out.println("Client đã kết nối sẵn rồi.");
                return;
            }

            try {
                socket = new Socket(serverIp, serverPort);
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                System.out.println("Đã kết nối thành công tới Server!");

                Thread listenerThread = new Thread(this::listenForMessages, "auction-client-listener");
                listenerThread.setDaemon(true);
                listenerThread.start();
            } catch (Exception e) {
                System.err.println("Không thể kết nối tới Server: " + e.getMessage());
                cleanUpVariables();
            }
        }
    }

    public void sendCommand(Command command, Object payload) throws IOException {
        synchronized (writeLock) {
            ObjectOutputStream localOut = this.out;
            Socket localSocket = this.socket;
            if (localOut != null && localSocket != null && !localSocket.isClosed()) {
                DataPacket packet = new DataPacket(command, payload);
                localOut.writeObject(packet);
                localOut.flush();
                localOut.reset();
            } else {
                throw new IOException("Chưa kết nối tới Server hoặc luồng dữ liệu đã bị đóng.");
            }
        }
    }

    public void listenForMessages() {
        try {
            while (true) {
                ObjectInputStream localIn;
                Socket localSocket;

                synchronized (connectionLock) {
                    localIn = this.in;
                    localSocket = this.socket;
                    if (localIn == null || localSocket == null || localSocket.isClosed()) {
                        break;
                    }
                }

                try {
                    Object obj = localIn.readObject();
                    if (obj == null) break;

                    if (obj instanceof DataPacket) {
                        handleServerResponse((DataPacket) obj);
                    } else {
                        System.err.println("Dữ liệu không hợp lệ: " + obj.getClass().getName());
                    }

                } catch (ClassNotFoundException e) {
                    System.err.println("Không tìm thấy class gói tin: " + e.getMessage());
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    System.err.println("Lỗi xử lý logic gói tin: " + e.getMessage());
                }
            }
        } catch (EOFException e) {
            System.out.println("Server đã chủ động đóng kết nối (EOF).");
        } catch (SocketException e) {
            System.out.println("Kết nối Socket đã bị ngắt.");
        } catch (IOException e) {
            System.err.println("Lỗi kết nối I/O: " + e.getMessage());
        } finally {
            closeConnection();
            System.out.println("Luồng lắng nghe đã kết thúc.");
        }
    }

    private void handleServerResponse(DataPacket response) {
        Command command = response.command();

        // 1. XỬ LÝ LỆNH ĐĂNG XUẤT THÀNH CÔNG (LOGOUT_RESULT)
        if (Command.LOGOUT_RESULT.equals(command)) {
            System.out.println("[Global Clean] Đăng xuất thành công. Tiến hành dọn dẹp giao diện...");
            Platform.runLater(() -> executeGlobalUiCleanup(null));
            return;
        }

        // 2. XỬ LÝ KHI BỊ HỆ THỐNG ĐÁ (FORCE_LOGOUT)
        if (Command.FORCE_LOGOUT.equals(command) || Command.FORCE_LOGOUT_MULTIPLE_USER.equals(command)) {
            System.out.println("[Global SSO] Nhận lệnh FORCE_LOGOUT từ Server.");

            String reason = "Tài khoản của bạn đã được đăng nhập từ một thiết bị khác.";
            if (response.payload() instanceof Map<?, ?> data && data.containsKey("reason")) {
                reason = String.valueOf(data.get("reason"));
            }

            final String finalReason = reason;
            Platform.runLater(() -> executeGlobalUiCleanup(finalReason));
            return;
        }

        // 🌟 SỬA: Phát tín hiệu cho TẤT CẢ các màn hình đang sống cùng nghe chung
        if (!listeners.isEmpty()) {
            for (ServerListener listener : listeners) {
                try {
                    listener.onServerResponse(response);
                } catch (Exception e) {
                    System.err.println("Lỗi chuyển tiếp gói tin tại " + listener.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void executeGlobalUiCleanup(String alertMessage) {
        if (alertMessage != null) {
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle("Cảnh Báo Hệ Thống");
                alert.setHeaderText(null);
                alert.setContentText(alertMessage);
                alert.showAndWait();
            } catch (Exception ignored) {}
        }

        try {
            model.User.UserSession.cleanUserSession();
            model.Items.ItemSession.cleanItemSession();
        } catch (Exception e) {
            System.err.println("Lỗi dọn dẹp Session: " + e.getMessage());
        }

        closeConnection();

        // 🌟 BỔ SUNG: Xóa sạch danh sách listener cũ khi đăng xuất hệ thống
        listeners.clear();

        try {
            java.util.List<javafx.stage.Window> openWindows = new java.util.ArrayList<>(javafx.stage.Window.getWindows());
            if (!openWindows.isEmpty()) {
                javafx.stage.Stage primaryStage = (javafx.stage.Stage) openWindows.get(0);

                for (int i = 1; i < openWindows.size(); i++) {
                    if (openWindows.get(i) instanceof javafx.stage.Stage stage) {
                        stage.close();
                    }
                }

                controller.SceneHelper.changeScene(primaryStage.getScene().getRoot(), "/fxml/LoginView.fxml");
                System.out.println("[Global Clean] Hệ thống đã quay về màn hình LoginView.");
            }
        } catch (Exception e) {
            System.err.println("Lỗi điều hướng JavaFX: " + e.getMessage());
            System.exit(0);
        }
    }

    public void closeConnection() {
        synchronized (connectionLock) {
            if (socket == null && in == null && out == null) return;
            System.out.println("Đang đóng kết nối Client...");

            if (out != null) { try { out.close(); } catch (IOException ignored) {} out = null; }
            if (in != null) { try { in.close(); } catch (IOException ignored) {} in = null; }
            if (socket != null && !socket.isClosed()) { try { socket.close(); } catch (IOException ignored) {} socket = null; }
        }
    }

    public boolean isConnected() {
        synchronized (connectionLock) {
            return socket != null && !socket.isClosed() && socket.isConnected();
        }
    }

    private void cleanUpVariables() {
        synchronized (connectionLock) {
            in = null;
            out = null;
            socket = null;
        }
    }
}