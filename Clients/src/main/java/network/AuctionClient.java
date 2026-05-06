package network;

import java.io.*;
import java.net.Socket;

/**
 * Client mạng (singleton) chịu trách nhiệm:
 * <ul>
 *   <li>Tạo và giữ kết nối socket đến Server.</li>
 *   <li>Gửi yêu cầu dạng {@link DataPacket}.</li>
 *   <li>Chạy luồng nền để lắng nghe phản hồi từ Server và chuyển tiếp cho {@link ServerListener} hiện tại.</li>
 * </ul>
 *
 * <p><b>Ràng buộc thiết kế</b>:
 * <ul>
 *   <li>Singleton: trong 1 phiên chạy app chỉ có 1 kết nối client được quản lý tập trung.</li>
 *   <li>Object streams: Output phải khởi tạo trước Input để tránh deadlock khi bắt tay header.</li>
 *   <li>Ghi stream không an toàn đa luồng: dùng {@code writeLock} để serialize thao tác {@code writeObject}.</li>
 * </ul>
 */
public class AuctionClient {
    private static AuctionClient instance;

    /** Socket đang kết nối tới Server; {@code null} nếu chưa connect hoặc đã lỗi. */
    private Socket socket;

    /**
     * Khoá ghi: {@link ObjectOutputStream} không thread-safe, nên chỉ cho phép 1 luồng ghi tại một thời điểm.
     * NOTE: Tránh interleave dữ liệu làm hỏng stream.
     */
    private final Object writeLock = new Object();

    /** Stream gửi object sang Server. */
    private ObjectOutputStream out;

    /** Stream nhận object từ Server. */
    private ObjectInputStream in;

    /**
     * Listener hiện tại (thường là Controller của màn hình đang hiển thị) để nhận callback khi có dữ liệu từ Server.
     * NOTE: Hệ thống hiện dùng 1 listener "active" tại một thời điểm (không phải event bus đa subscriber).
     */
    private ServerListener currentListener;

    /**
     * Precondition: Không có.
     * Postcondition: Không có (ngăn khởi tạo từ bên ngoài).
     * NOTE: Singleton.
     */
    private AuctionClient() {}

    /**
     * Precondition: Không có.
     * Postcondition: Trả về instance singleton; nếu chưa tồn tại thì tạo mới.
     * NOTE: synchronized để tránh tạo 2 instance trong môi trường đa luồng.
     * Method returns: {@link AuctionClient} singleton.
     */
    public static synchronized AuctionClient getInstance() {
        if (instance == null) {
            instance = new AuctionClient();
        }
        return instance;
    }

    /**
     * Precondition: {@code listener} có thể {@code null}; nếu {@code null} thì client sẽ không dispatch phản hồi cho UI.
     * Postcondition: {@code currentListener} được gán bằng {@code listener}.
     * NOTE: Thường được gọi trong {@code initialize()} của các Controller để "đăng ký màn hình hiện tại".
     * Method returns: nothing.
     */
    public void setListener(ServerListener listener) {
        this.currentListener = listener;
    }

    /**
     * Precondition: {@code serverIp} và {@code serverPort} hợp lệ và Server đang lắng nghe.
     * Postcondition: Nếu kết nối thành công thì {@code socket/out/in} được khởi tạo và 1 luồng nền listener được start.
     * NOTE: Khởi tạo {@link ObjectOutputStream} trước rồi {@link ObjectInputStream} để tránh treo hai đầu vì header handshake.
     * Method returns: nothing.
     * NOTE: Nếu kết nối thất bại, method sẽ log ra stderr và giữ trạng thái có thể ở mức "chưa kết nối" (out/in có thể null).
     */
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

    /**
     * Precondition: Đã gọi {@link #connect(String, int)} thành công và {@code out != null}.
     * Postcondition: Một {@link DataPacket} được ghi lên stream (nếu {@code out != null}) và được {@code flush}.
     * NOTE: Method thread-safe ở mức ghi stream (dùng {@code writeLock}).
     * Method returns: nothing.
     * NOTE: Nếu {@code out == null} thì method sẽ im lặng (không gửi gì).
     * @throws IOException NOTE: Ném ra khi ghi stream lỗi (mất kết nối, stream đóng, ...).
     */
    public void sendCommand(String command, Object payload) throws IOException {
        synchronized (writeLock) {
            if (out != null) {
                DataPacket packet = new DataPacket(command, payload);
                out.writeObject(packet);
                out.flush();
            }
        }
    }

    /**
     * Precondition: Đã kết nối và {@code in != null}.
     * Postcondition: Vòng lặp đọc sẽ chạy đến khi gặp EOF/mất kết nối/exception; trong quá trình chạy sẽ dispatch từng {@link DataPacket} cho listener.
     * NOTE: Đây là method chạy trên luồng nền (daemon thread) được tạo trong {@link #connect(String, int)}.
     * Method returns: nothing (vì là vòng lặp).
     * NOTE: Nếu Server đóng kết nối, {@link EOFException} sẽ xảy ra và loop kết thúc.
     */
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

    /**
     * Precondition: {@code response} khác {@code null} và là packet hợp lệ.
     * Postcondition: Nếu đã đăng ký {@code currentListener} thì listener được gọi 1 lần với packet này.
     * NOTE: Callback có thể chạy trên luồng nền; listener chịu trách nhiệm chuyển về UI thread nếu cần.
     * Method returns: nothing.
     */
    private void handleServerResponse(DataPacket response) {
        if (currentListener != null) {
            // Chuyền dữ liệu cho Controller đang hiển thị xử lý
            currentListener.onServerResponse(response);
        }
    }
}