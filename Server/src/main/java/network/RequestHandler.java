package network;

import java.io.ObjectOutputStream;


/**
 * Hợp đồng chung cho mọi handler xử lý command gửi qua socket.
 */
public interface RequestHandler {
    /**
     * Precondition: payload có kiểu dữ liệu đúng với command mà handler này xử lý, và out là
     * ObjectOutputStream đang mở của client gửi request.
     * Postcondition: Handler xử lý payload và thường ghi một DataPacket response về client.
     * Method không có giá trị trả về.
     * NOTE: Implement có thể phát sinh RuntimeException nếu cast payload, xử lý service,
     * hoặc ghi stream bị lỗi.
     */
    void handle(Object payload, ObjectOutputStream out);}
