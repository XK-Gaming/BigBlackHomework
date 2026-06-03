package network;

import java.io.ObjectOutputStream;

// Interface xử lý request.
public interface RequestHandler {
    void handle(Object payload, ObjectOutputStream out);
}
