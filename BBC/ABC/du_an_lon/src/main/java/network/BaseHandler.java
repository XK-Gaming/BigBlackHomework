package network;

import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;

public abstract class BaseHandler{
    protected Gson gson = Converters.registerAll(new GsonBuilder()).create();

    // Đây chính là hàm sendResponse bạn đang tìm
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