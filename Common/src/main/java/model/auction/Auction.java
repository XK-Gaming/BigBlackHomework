package model.auction;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.Entity.Entity;
import model.Items.Item;

/**
 * Dai dien cho mot phien dau gia cua duy nhat mot item.
 * Lop nay quan ly trang thai phien dau gia va lich su bid.
 */
public class Auction extends Entity implements Serializable {
    private static final long serialVersionUID = 1L;
    private long itemId;
    private Item item;
    private String sellerID;
    private List<BidTransaction> bidHistory = new ArrayList<>();
    private AuctionStatus status;
    private String leadingBidder;


    public Auction(String id, Item item, String sellerID, Instant createdAt) {
        super(id, createdAt);
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        this.item = item;
        this.sellerID = sellerID;
        this.status = AuctionStatus.OPEN;
    }

    public Auction() {

    }

    @Override
    public String printInfo() {
        return "";
    }

    public Item getItem() {
        return item;
    }

    public String getSellerID() {
        return sellerID;
    }

    public AuctionStatus getStatus() {
        return status;
    }


    public String getDefaultBidder() {
        return "Người bán";
    }

    public String getLeadingBidder() {
        return leadingBidder;
    }

    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    public void setStatus(AuctionStatus status){
        this.status = status;
    }
    public void setLeadingBidder(String leadingBidder){
        this.leadingBidder = leadingBidder;
    }

    public void setItemId(long idItem) {
        this.itemId = idItem;
    }

    /** Khóa sản phẩm trong bảng auction_items (cột id_item); dùng khi hydrate từ DB mà chưa gắn {@link Item}. */
    public long getItemId() {
        return itemId;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public void setbidHistory(List<BidTransaction> history) {
        this.bidHistory = history;
    }
}