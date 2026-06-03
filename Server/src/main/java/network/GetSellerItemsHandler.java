package network;

import dao.DAOAuction_Items;
import dao.DAOItems;
import model.Items.Item;
import model.auction.AuctionStatus;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// Request sản phẩm seller.
public class GetSellerItemsHandler extends BaseHandler implements RequestHandler {

    // Xử lý request sản phẩm seller.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {

        Map<String, Object> response = new HashMap<>();

        try {

            String sellerUsername = String.valueOf(payload);

            if (sellerUsername == null || sellerUsername.trim().isEmpty()) {
                throw new IllegalArgumentException("Username không hợp lệ!");
            }

            ArrayList<Item> sellerItems = DAOItems.getInstance().selectBySellerId(sellerUsername);

            Map<Integer, String> statusCache = new HashMap<>();

            if (sellerItems != null) {
                for (Item item : sellerItems) {

                    var auctionItem = DAOAuction_Items.getInstance().selectByItemId(item);
                    if (auctionItem != null && auctionItem.getStatus() != null) {

                        statusCache.put(item.getDatabaseId(), auctionItem.getStatus().name());
                    } else {

                        statusCache.put(item.getDatabaseId(), AuctionStatus.OPEN.name());
                    }
                }
            } else {
                sellerItems = new ArrayList<>();
            }

            response.put("success", true);
            response.put("items", sellerItems);
            response.put("statusCache", statusCache);

        } catch (Exception e) {
            e.printStackTrace();

            fillErrorResponse(response, e);
        }

        sendResponse(out, Command.GET_SELLER_ITEMS_RESULT, response);
    }
}
