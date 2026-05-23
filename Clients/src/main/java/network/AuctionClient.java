package network;

import javafx.application.Platform;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class AuctionClient {
    private volatile Socket socket;
    private volatile ObjectOutputStream out;
    private volatile ObjectInputStream in;

    private final Object writeLock = new Object();
    private final Object connectionLock = new Object();

    // Đảm bảo thay đổi giữa các luồng được thấy ngay lập tức
    private volatile ServerListener currentListener;

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

    public void setListener(ServerListener listener) {
        this.currentListener = listener;
    }

    public void connect(String serverIp, int serverPort) {
        synchronized (connectionLock) {
            if (socket != null && !socket.isClosed()) {
                System.out.println("Client đã kết nối sẵn rồi.");
                return;
            }

            try {
                socket = new Socket(serverIp, serverPort);
                // Khởi tạo OutputStream trước và flush để tránh treo (Deadlock) ở phía Server khi tạo InputStream
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

    // Hàm dùng để gửi DataPacket lên Server
    public void sendCommand(Command command, Object payload) throws IOException {
        synchronized (writeLock) {
            ObjectOutputStream localOut = this.out;
            Socket localSocket = this.socket;
            if (localOut != null && localSocket != null && !localSocket.isClosed()) {
                DataPacket packet = new DataPacket(command, payload);
                localOut.writeObject(packet);
                localOut.flush();
                localOut.reset(); // Xóa cache để tránh tràn bộ nhớ khi chạy lâu dài
            } else {
                throw new IOException("Chưa kết nối tới Server hoặc luồng ra đã bị đóng.");
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
                    // Tác vụ blocking nằm ngoài khối synchronized -> Rất tốt!
                    Object obj = localIn.readObject();
                    if (obj == null) {
                        break;
                    }

                    if (obj instanceof DataPacket) {
                        handleServerResponse((DataPacket) obj);
                    } else {
                        System.err.println("Dữ liệu nhận được không phải là DataPacket: " + obj.getClass().getName());
                    }

                } catch (ClassNotFoundException e) {
                    System.err.println("Không tìm thấy class gói tin: " + e.getMessage());
                } catch (IOException e) {
                    // Đẩy lỗi ra ngoài để rơi vào khối dọn dẹp tài nguyên
                    throw e;
                } catch (Exception e) {
                    System.err.println("Lỗi không nghiêm trọng khi xử lý logic gói tin: " + e.getMessage());
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
            System.out.println("Luồng lắng nghe đã kết thúc và dọn dẹp tài nguyên.");
        }
    }

    private void handleServerResponse(DataPacket response) {
        // Gán vào biến local để tránh NullPointerException do lỗi "Check-then-Act" trong đa luồng
        ServerListener listener = this.currentListener;
        if (listener != null) {
            try {
                listener.onServerResponse(response);
            } catch (Exception e) {
                System.err.println("Lỗi xử lý logic của Listener: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void closeConnection() {
        Socket socketToClose;
        ObjectInputStream inToClose;
        ObjectOutputStream outToClose;

        synchronized (connectionLock) {
            if (socket == null && in == null && out == null) {
                return;
            }
            System.out.println("Đang đóng kết nối Client...");

            socketToClose = this.socket;
            inToClose = this.in;
            outToClose = this.out;

            cleanUpVariables();
        }

        // Đóng các luồng stream trước một cách an toàn
        if (outToClose != null) {
            try { outToClose.close(); } catch (IOException e) { System.err.println("Lỗi đóng luồng ra: " + e.getMessage()); }
        }
        if (inToClose != null) {
            try { inToClose.close(); } catch (IOException e) { System.err.println("Lỗi đóng luồng vào: " + e.getMessage()); }
        }
        if (socketToClose != null && !socketToClose.isClosed()) {
            try { socketToClose.close(); } catch (IOException e) { System.err.println("Lỗi đóng socket: " + e.getMessage()); }
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