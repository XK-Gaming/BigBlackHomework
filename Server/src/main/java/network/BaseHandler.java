package network;

import model.exception.AuctionException;
import model.exception.BidRejectedException;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

// Xử lý lỗi/response chung.
public abstract class BaseHandler{

    protected void fillErrorResponse(Map<String, Object> response, Throwable t) {
        response.put("success", false);
        String msg = t.getMessage();
        response.put("message", (msg != null && !msg.isBlank()) ? msg : "Lỗi không xác định.");
        response.put("errorType", t.getClass().getSimpleName());
        if (t instanceof BidRejectedException br) {
            response.put("reason", br.getReason().name());
        }
        if (t instanceof AuctionException && t.getCause() != null) {
            response.put("causeType", t.getCause().getClass().getSimpleName());
        }
    }

    protected void sendResponse(ObjectOutputStream out, Command command, Object payload) {
        DataPacket responsePacket = new DataPacket(command, payload);

        try {
            synchronized (out) {
                out.reset();
                out.writeObject(responsePacket);
                out.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
