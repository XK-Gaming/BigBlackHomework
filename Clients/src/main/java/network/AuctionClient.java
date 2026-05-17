package network;

import javafx.application.Platform;
import java.io.*;
import java.net.Socket;

public class AuctionClient {
    private Socket socket; // Tạ đối tượng socket
    private final Object writeLock = new Object();
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ServerListener currentListener;

    // Triển khai Singleton (chỉ cho 1 client kết nối duy nhất _ trên một thiết bị)
    private static AuctionClient instance;
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
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            System.out.println("Đã kết nối thành công tới Server!");
            Thread listenerThread = new Thread(this::listenForMessages, "auction-client-listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
        } catch (Exception e) {System.err.println("Không thể kết nối tới Server: " + e.getMessage());}
    }

    // Hàm dùng để gửi DataPacket lên Server
    public void sendCommand(Command command, Object payload) throws IOException {
        synchronized (writeLock) {
            if (out != null) {
                DataPacket packet = new DataPacket(command, payload);
                out.reset();
                out.writeObject(packet);
                out.flush();
            }
        }
    }

    public void listenForMessages() {
        try {
            while (true) {
                // Đọc nguyên đối tượng DataPacket từ Server
                DataPacket response = (DataPacket) in.readObject();
                // Phân loại kết quả trả về
                handleServerResponse(response);
            }
        } catch (EOFException e) {
            System.err.println("Server đã đóng kết nối.");
        } catch (Exception e) {e.printStackTrace();}
    }
    // Hàm xử lý logic khi nhận được tin từ Server
    private void handleServerResponse(DataPacket response) {
        if (currentListener != null) {
            // Chuyền dữ liệu cho Controller đang hiển thị xử lý
            currentListener.onServerResponse(response);
        }
    }
}
