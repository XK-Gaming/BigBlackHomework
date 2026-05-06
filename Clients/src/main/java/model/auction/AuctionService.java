package model.auction;

import model.Items.Item;
import model.User.Bidder;
import model.User.User;
import network.AuctionClient;
import network.DataPacket;
import network.ServerListener;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Map;

/**
 * Service phía client để thao tác với phiên đấu giá thông qua server (request/response).
 *
 * <p>Cơ chế hoạt động:
 * <ul>
 *   <li>Gửi command qua {@link AuctionClient}.</li>
 *   <li>Chờ phản hồi bằng cơ chế {@code wait/notify} trong tối đa 5 giây.</li>
 * </ul>
 *
 * <p>NOTE: Kiến trúc hiện tại chỉ hỗ trợ 1 {@link ServerListener} active trong {@link AuctionClient}.
 * Việc {@code client.setListener(this)} ở constructor có thể "giật" listener của UI khác nếu dùng song song.
 */
public class AuctionService implements ServerListener {
    private final AuctionClient client = AuctionClient.getInstance();
    private Auction currentAuction;
    private boolean isWaitingForResponse = false;

    /**
     * Precondition: Client đã connect hoặc sẽ connect trước khi gọi service.
     * Postcondition: Service đăng ký làm listener hiện tại của {@link AuctionClient}.
     * NOTE: Có thể xung đột với các Controller cũng đăng ký listener.
     * Method returns: đối tượng {@link AuctionService} mới.
     */
    public AuctionService() {
        client.setListener(this);
    }

    /**
     * Precondition: {@code item} khác null và có {@code databaseId} hợp lệ; server đang chạy.
     * Postcondition:
     * - Nếu {@code currentAuction} đang cache cho đúng item -> trả về ngay.
     * - Nếu không -> gửi {@code GET_AUCTION} và chờ {@code GET_AUCTION_RESULT} (tối đa 5s), sau đó trả về {@code currentAuction}.
     * NOTE: Nếu quá timeout mà không có phản hồi thì {@code currentAuction} có thể vẫn null hoặc dữ liệu cũ.
     * Method returns: {@link Auction} hoặc null.
     * @throws IOException NOTE: Ném ra nếu gửi command lỗi.
     */
    public Auction getAuction(Item item) throws IOException {
        if (currentAuction != null && currentAuction.getItem() != null && 
            currentAuction.getItem().getDatabaseId()==item.getDatabaseId()) {
            return currentAuction;
        }

        client.sendCommand("GET_AUCTION", item.getDatabaseId());
        waitForResponse();
        return currentAuction;
    }

    /**
     * Precondition:
     * - {@code auction}, {@code item}, {@code user} khác null.
     * - {@code priceInput} là chuỗi số (hoặc sẽ báo lỗi).
     * Postcondition:
     * - Nếu validate fail -> trả về message lỗi, không gửi bid.
     * - Nếu pass -> gửi {@code BID} lên server và chờ phản hồi; nếu không lỗi format thì trả "SUCCESS".
     * NOTE: Kết quả "SUCCESS" ở đây nghĩa là đã gửi và nhận phản hồi, không nhất thiết reflect đầy đủ state server nếu timeout.
     * Method returns: "SUCCESS" hoặc message lỗi tiếng Việt.
     * @throws IOException NOTE: Ném ra nếu gửi command lỗi.
     */
    public String processBid(Auction auction, Item item, User user, String priceInput) throws IOException {
        try {
            double amount = Double.parseDouble(priceInput);

            if (amount <= item.getCurrentHighestPrice()) {
                return "Giá đặt phải cao hơn giá hiện tại!";
            }

            if (auction.getStatus() != AuctionStatus.RUNNING) {
                return "Phiên đấu giá hiện không diễn ra.";
            }

            client.sendCommand("BID", Map.of(
                    "itemId", item.getDatabaseId(),
                    "bidderId", user.getUsername(),
                    "amount", String.valueOf(amount)
            ));

            waitForResponse();

            return "SUCCESS";
        } catch (NumberFormatException e) {
            return "Vui lòng nhập số tiền hợp lệ.";
        }
    }

    /**
     * Precondition: Được gọi sau khi đã gửi command cần phản hồi.
     * Postcondition: Thread hiện tại sẽ chờ tối đa 5 giây hoặc tới khi {@link #onServerResponse(DataPacket)} gọi {@code notify()}.
     * NOTE: Nếu bị interrupt sẽ set interrupt flag lại.
     * Method returns: nothing.
     */
    private synchronized void waitForResponse() {
        isWaitingForResponse = true;
        try {
            wait(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        isWaitingForResponse = false;
    }

    @Override
    /**
     * Precondition: response là {@link DataPacket} hợp lệ.
     * Postcondition:
     * - Nếu {@code GET_AUCTION_RESULT}: cập nhật {@code currentAuction} rồi {@code notify()}.
     * - Nếu {@code BID_UPDATE}: cập nhật {@code currentAuction} hoặc giá hiện tại rồi {@code notify()}.
     * - Nếu {@code BID_RESULT}: nếu success thì cập nhật giá hiện tại rồi {@code notify()}.
     * NOTE: Method synchronized để phối hợp với {@link #waitForResponse()}.
     * Method returns: nothing.
     */
    public synchronized void onServerResponse(DataPacket response) {
        String command = response.getCommand();

        if ("GET_AUCTION_RESULT".equals(command)) {
            currentAuction = (Auction) response.getPayload();
            notify();
        }
        else if ("BID_UPDATE".equals(command)) {
            Map<String, Object> update = (Map<String, Object>) response.getPayload();
            Object auctionObj = update.get("auction");
            if (auctionObj instanceof Auction) {
                currentAuction = (Auction) auctionObj;
            } else if (currentAuction != null) {
                Object newPriceObj = update.get("newPrice");
                if (newPriceObj instanceof Number) {
                    currentAuction.getItem().setCurrentHighestPrice(((Number) newPriceObj).doubleValue());
                }
            }
            notify();
        }
        else if ("BID_RESULT".equals(command)) {
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            boolean isSuccess = (boolean) result.get("success");
            if (isSuccess && currentAuction != null) {
                double newPrice = (double) result.get("newPrice");
                currentAuction.getItem().setCurrentHighestPrice(newPrice);
            }
            notify();
        }
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không thay đổi state.
     * Method returns: Chuỗi giá đã format kèm "VNĐ".
     */
    public String formatPrice(double price) {
        return new DecimalFormat("#,###").format(price) + " VNĐ";
    }
}