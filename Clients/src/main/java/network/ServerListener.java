package network;

/**
 * Hợp đồng callback để các Controller (hoặc lớp UI khác) nhận dữ liệu trả về từ Server.
 *
 * <p>Vai trò:
 * <ul>
 *   <li>{@link network.AuctionClient} nhận {@link DataPacket} từ socket.</li>
 *   <li>Sau đó chuyển tiếp (dispatch) cho {@link ServerListener} hiện tại đang được đăng ký.</li>
 * </ul>
 */
public interface ServerListener {

    /**
     * Precondition: {@code response} là một {@link DataPacket} hợp lệ được Server gửi về; {@code response.getCommand()} thuộc tập lệnh đã thoả thuận.
     * Postcondition: Listener đã xử lý response (cập nhật UI/state tuỳ từng màn hình); không có yêu cầu bắt buộc về trạng thái sau xử lý.
     * NOTE: Thực tế callback có thể được gọi từ luồng nền (I/O thread). Nếu có thao tác UI JavaFX, cần chuyển về UI thread (ví dụ {@code Platform.runLater}).
     * Method returns: nothing.
     * NOTE: Implementations nên tự bắt/hiển thị lỗi; exception không kiểm soát có thể làm hỏng luồng nhận tin.
     */
    void onServerResponse(DataPacket response);
}