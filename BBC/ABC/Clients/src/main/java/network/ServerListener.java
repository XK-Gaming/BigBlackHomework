package network;

import network.DataPacket;

public interface ServerListener {
    // Hàm này sẽ được gọi mỗi khi Server gửi tin nhắn về
    void onServerResponse(DataPacket response);
}