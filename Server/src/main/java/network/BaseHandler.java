package network;

import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;

/**
 * Class cha dùng chung cho các request handler.
 *
 * Trách nhiệm class: cung cấp Gson để chuyển đổi payload và hàm gửi response thống nhất.
 */
public abstract class BaseHandler{
    /** Gson dùng khi handler cần chuyển payload Object/Map về kiểu dữ liệu cụ thể. */
    protected Gson gson = Converters.registerAll(new GsonBuilder()).create();

    // Đây chính là hàm sendResponse bạn đang tìm
    /**
     * Precondition: out là ObjectOutputStream đang mở của client, command là tên response mà
     * client biết cách xử lý.
     * Postcondition: Ghi DataPacket(command, payload) xuống output stream và flush stream.
     * Method không trả về giá trị.
     * NOTE: Ném RuntimeException nếu không thể ghi response xuống stream.
     */
    protected void sendResponse(ObjectOutputStream out, String command, Object payload) {
        DataPacket responsePacket = new DataPacket(command, payload);

        // Ghi trực tiếp đối tượng
        try {
            out.writeObject(responsePacket);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
