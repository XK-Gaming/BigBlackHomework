package network;

import model.User.User;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * AutoBid: nhận yêu cầu bật/tắt từ client, validate payload cơ bản rồi chuyển cho AutoBidManager xử lý.
 */
public class AutoBidHandler extends BaseHandler implements RequestHandler {
    private final ClientHandler clientHandler;

    public AutoBidHandler(ClientHandler clientHandler) {
        this.clientHandler = clientHandler;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (!(payload instanceof Map<?, ?> payloadMap)) {
                throw new IllegalArgumentException("Payload AutoBid khong hop le.");
            }

            String itemId = stringValue(payloadMap.get("itemId"));
            String username = authenticatedUsername(payloadMap);
            boolean enabled = Boolean.parseBoolean(stringValue(payloadMap.get("enabled")));

            if (!enabled) {
                response = AutoBidManager.getInstance().disable(itemId, username, "AutoBid da tat.");
            } else {
                double maxBidAllow = parsePositiveDouble(payloadMap.get("maxBidAllow"), "MaxBidAllow");
                double bidGap = parsePositiveDouble(payloadMap.get("bidGap"), "BidGap");
                response = AutoBidManager.getInstance().enable(itemId, username, maxBidAllow, bidGap);
            }
        } catch (Exception e) {
            fillErrorResponse(response, e);
            response.put("enabled", false);
        }

        sendResponse(out, Command.SET_AUTO_BID_RESULT, response);
    }

    private String authenticatedUsername(Map<?, ?> payloadMap) {
        User user = clientHandler.getUser();
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return stringValue(payloadMap.get("userId"));
    }

    private static double parsePositiveDouble(Object value, String fieldName) {
        double parsed = Double.parseDouble(stringValue(value));
        if (parsed <= 0) {
            throw new IllegalArgumentException(fieldName + " phai lon hon 0.");
        }
        return parsed;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
