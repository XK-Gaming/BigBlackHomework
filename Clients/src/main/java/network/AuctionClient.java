package network;

import javafx.application.Platform;
import java.io.*;
import java.net.Socket;

public class AuctionClient {
    private static AuctionClient instance;
    private Socket socket;

    // ObjectOutputStream không an toàn đa luồng — chỉ một luồng được ghi tại một thời điểm
    private final Object writeLock = new Object();

    // Sử dụng Object Stream để truyền đối tượng trực tiếp
    private ObjectOutputStream out;
    private ObjectInputStream in;

    // Biến lưu trữ Controller đang hiển thị trên màn hình
    private ServerListener currentListener;

    // Triển khai Singleton (chỉ cho 1 client kết nối duy nhất)
    private AuctionClient() {}

    public static synchronized AuctionClient getInstance() {
        if (instance == null) {
            instance = new AuctionClient();
        }
        return instance;
    }

    // Hàm để Controller đăng ký lắng nghe
    public void setListener(ServerListener listener) {
        this.currentListener = listener;
    }

    // Khởi tạo kết nối tới Server
    public void connect(String serverIp, int serverPort) {
        try {
            socket = new Socket(serverIp, serverPort);

            // QUAN TRỌNG: Khởi tạo Output trước Input
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // Đẩy header đi để tránh treo luồng 2 đầu

            in = new ObjectInputStream(socket.getInputStream());

            System.out.println("Đã kết nối thành công tới Server!");

            // Bắt buộc: Mở một luồng chạy ngầm để liên tục nghe Server
            Thread listenerThread = new Thread(this::listenForMessages, "auction-client-listener");
            listenerThread.setDaemon(true); // Tự tắt luồng khi tắt app
            listenerThread.start();

        } catch (Exception e) {
            System.err.println("Không thể kết nối tới Server: " + e.getMessage());
        }
    }

    // Hàm dùng để gửi DataPacket lên Server
    public void sendCommand(String command, Object payload) throws IOException {
        synchronized (writeLock) {
            if (out != null) {
                DataPacket packet = new DataPacket(command, payload);
                out.writeObject(packet);
                out.flush();
            }
        }
    }

    // Luồng ngầm: Liên tục nghe phản hồi từ Server
    public void listenForMessages() {
        try {
            // Lặp vô tận để nghe dữ liệu
            while (true) {
                // Đọc nguyên đối tượng DataPacket từ Server
                DataPacket response = (DataPacket) in.readObject();

                // Phân loại kết quả trả về
                handleServerResponse(response);
            }
        } catch (EOFException e) {
            // Lỗi này văng ra khi Server chủ động ngắt kết nối
            System.out.println("Đã ngắt kết nối khỏi Server (EOF).");
        } catch (Exception e) {
            System.out.println("Lỗi luồng lắng nghe hoặc mất kết nối: " + e.getMessage());
        }
    }

    // Hàm xử lý logic khi nhận được tin từ Server
    private void handleServerResponse(DataPacket response) {
        if (currentListener != null) {
            // Chuyền dữ liệu cho Controller đang hiển thị xử lý
            currentListener.onServerResponse(response);
        }
    }
}