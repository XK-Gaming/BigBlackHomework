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

public class AuctionService implements ServerListener {
    private final AuctionClient client = AuctionClient.getInstance();
    private Auction currentAuction;
    private boolean isWaitingForResponse = false;

    public AuctionService() {
        client.setListener(this);
    }

    public Auction getAuction(Item item) throws IOException {
        if (currentAuction != null && currentAuction.getItem() != null && 
            currentAuction.getItem().getDatabaseId()==item.getDatabaseId()) {
            return currentAuction;
        }

        client.sendCommand("GET_AUCTION", item.getDatabaseId());
        waitForResponse();
        return currentAuction;
    }

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

    public String formatPrice(double price) {
        return new DecimalFormat("#,###").format(price) + " VNĐ";
    }
}