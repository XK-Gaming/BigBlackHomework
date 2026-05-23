package network;

import java.io.Serial;
import java.io.Serializable;

/**
 * @param command Sử dụng Enum thay vì String
 */
public record DataPacket(Command command, Object payload) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

}