package network;

import java.io.ObjectOutputStream;


public interface RequestHandler {
    void handle(Object payload, ObjectOutputStream out);
}