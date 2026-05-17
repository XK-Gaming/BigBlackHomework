package network;

import java.io.IOException;
import java.io.ObjectOutputStream;


public abstract class BaseHandler{
    // Đây chính là hàm sendResponse bạn đang tìm
    protected void sendResponse(ObjectOutputStream out, Command command, Object payload) {
        DataPacket responsePacket = new DataPacket(command, payload);

        try {
            synchronized (out) {
                out.reset();
                out.writeObject(responsePacket);
                out.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
