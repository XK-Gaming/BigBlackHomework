package network;
import java.io.Serializable;
public class DataPacket implements Serializable{
    private static final long serialVersionUID = 1L;
    private String command;
    private Object payload; // Dùng kiểu Object để có thể chứa bất kỳ loại class nào (User, Item, String...)

    // Constructor
    public DataPacket(String command, Object payload) {
        this.command = command;
        this.payload = payload;
    }

    public String getCommand() { return command; }
    public Object getPayload() { return payload; }
}