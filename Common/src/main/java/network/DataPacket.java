package network;

import java.io.Serializable;

public class DataPacket implements Serializable {
    private static final long serialVersionUID = 1L;
    private Command command; // Sử dụng Enum thay vì String
    private Object payload;

    public DataPacket(Command command, Object payload) {
        this.command = command;
        this.payload = payload;
    }

    public Command getCommand() { return command; }
    public Object getPayload() { return payload; }

}