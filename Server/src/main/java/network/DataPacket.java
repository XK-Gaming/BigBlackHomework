package network;
import java.io.Serializable;
/**
 * Gói tin có thể serialize để client và server trao đổi qua ObjectInputStream/ObjectOutputStream.
 *
 * Trách nhiệm class: gom tên lệnh (command) và dữ liệu đi kèm (payload) vào cùng một object
 * để ClientHandler biết phải chuyển request cho handler nào.
 */
public class DataPacket implements Serializable{
    private static final long serialVersionUID = 1L;
    /** Tên lệnh dùng để ClientHandler chọn RequestHandler tương ứng. */
    private String command;
    /** Dữ liệu request/response tùy theo từng command. */
    private Object payload; // Dùng kiểu Object để có thể chứa bất kỳ loại class nào (User, Item, String...)

    // Constructor
    /**
     * Precondition: Bên gọi truyền command mà phía nhận hiểu được, và payload có kiểu dữ liệu
     * đúng với hợp đồng của command đó.
     * Postcondition: Tạo một DataPacket chứa command và payload được truyền vào.
     * Method trả về một instance DataPacket mới.
     */
    public DataPacket(String command, Object payload) {
        this.command = command;
        this.payload = payload;
    }

    /**
     * Precondition: DataPacket đã được khởi tạo.
     * Postcondition: Method trả về command dùng để định tuyến sang handler.
     */
    public String getCommand() { return command; }

    /**
     * Precondition: DataPacket đã được khởi tạo.
     * Postcondition: Method trả về payload gốc để handler xử lý.
     */
    public Object getPayload() { return payload; }
}
