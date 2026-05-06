package network;
import java.io.Serializable;

/**
 * Đối tượng gói tin (message envelope) dùng để trao đổi giữa Client và Server qua {@link java.io.ObjectOutputStream}/{@link java.io.ObjectInputStream}.
 *
 * <p><b>Ý nghĩa thiết kế</b>:
 * <ul>
 *   <li><b>command</b>: định danh loại yêu cầu/phản hồi (ví dụ: {@code LOGIN}, {@code BID_UPDATE}).</li>
 *   <li><b>payload</b>: dữ liệu đi kèm; có thể là {@link java.util.Map}, model (User/Item/Auction...), hoặc {@code null} tuỳ lệnh.</li>
 * </ul>
 *
 * <p><b>Lưu ý</b>: {@code payload} là {@link Object} nên khi nhận về cần ép kiểu đúng với quy ước giữa hai phía.
 */
public class DataPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Mã lệnh/loại message để phía nhận phân nhánh xử lý. */
    private String command;

    /** Dữ liệu kèm theo message; kiểu động để có thể mang nhiều loại đối tượng khác nhau. */
    private Object payload; // Dùng kiểu Object để có thể chứa bất kỳ loại class nào (User, Item, String...)

    /**
     * Precondition: {@code command} không được {@code null} và phải thuộc tập lệnh mà hai phía (Client/Server) đã thống nhất.
     * Postcondition: Tạo một {@link DataPacket} mới với {@code command} và {@code payload} tương ứng.
     * NOTE: {@code payload} có thể là {@code null} nếu lệnh không cần dữ liệu.
     * Method returns: đối tượng {@link DataPacket} vừa được khởi tạo.
     */
    public DataPacket(String command, Object payload) {
        this.command = command;
        this.payload = payload;
    }

    /**
     * Precondition: {@link DataPacket} đã được khởi tạo hợp lệ.
     * Postcondition: Không thay đổi trạng thái đối tượng.
     * Method returns: giá trị {@code command} hiện tại.
     */
    public String getCommand() { return command; }

    /**
     * Precondition: {@link DataPacket} đã được khởi tạo hợp lệ.
     * Postcondition: Không thay đổi trạng thái đối tượng.
     * NOTE: Giá trị trả về là {@link Object}; phía gọi phải ép kiểu đúng theo {@code command}.
     * Method returns: giá trị {@code payload} hiện tại (có thể {@code null}).
     */
    public Object getPayload() { return payload; }
}