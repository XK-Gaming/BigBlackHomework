package network;

// Listener nhận response server.
public interface ServerListener {

    void onServerResponse(DataPacket response);

}
