package network;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * ## JUnit: test goi tin Socket Serializable dung chung giua client va server.
 */
class DataPacketSerializationTest {

    /**
     * ## Test protocol: DataPacket van giu command va payload sau khi serialize/deserialize.
     */
    @Test
    void dataPacketCanBeSerializedBetweenClientAndServer() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("success", false);
        payload.put("reason", "PRICE_TOO_LOW");
        DataPacket packet = new DataPacket(Command.BID_RESULT, payload);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(packet);
        }

        DataPacket restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (DataPacket) in.readObject();
        }

        assertEquals(Command.BID_RESULT, restored.command());
        Map<?, ?> restoredPayload = assertInstanceOf(Map.class, restored.payload());
        assertEquals(false, restoredPayload.get("success"));
        assertEquals("PRICE_TOO_LOW", restoredPayload.get("reason"));
    }
}
