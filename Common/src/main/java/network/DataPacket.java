package network;

import java.io.Serial;
import java.io.Serializable;

// Gói dữ liệu socket.
public record DataPacket(Command command, Object payload) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

}
