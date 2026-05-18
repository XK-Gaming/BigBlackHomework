package network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * ## JUnit helper: chay handler bang ObjectOutputStream that de kiem tra DataPacket tra ve client.
 */
final class HandlerTestSupport {

    private HandlerTestSupport() {
    }

    /**
     * ## Test helper: khong chay server socket, chi goi truc tiep handler va doc response packet.
     */
    static DataPacket handle(RequestHandler handler, Object payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            handler.handle(payload, out);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (DataPacket) in.readObject();
        }
    }
}
