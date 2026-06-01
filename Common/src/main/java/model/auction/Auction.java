package model.auction;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dao.DAOAuction_Items;
import model.Entity.Entity;
import model.Items.Item;

/**
 * Dai dien cho mot phien dau gia cua duy nhat mot item.
 * Lop nay quan ly trang thai phien dau gia va lich su bid.
 */
public class Auction extends Entity implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Id item trong database, tương ứng auction_items.id_item. */
    private long itemId;
    /** Item đang được đấu giá; có thể được AuctionEngine load sau. */
    private Item item;
    /** Username/id của người bán sở hữu item. */
    private String sellerID;
    /** Lịch sử đặt giá của phiên đấu giá, theo thứ tự thời gian. */
    private List<BidTransaction> bidHistory = new ArrayList<>();
    /** Trạng thái vòng đời hiện tại của phiên đấu giá. */
    private AuctionStatus status;
    /** Username của người đang đặt giá cao nhất. */
    private String leadingBidder;


    /**
     * Precondition: item khác null, sellerID xác định người bán, createdAt được truyền vào.
     * Postcondition: Tạo Auction trạng thái OPEN và gắn với item/người bán.
     * NOTE: Ném IllegalArgumentException nếu item null.
     */
    public Auction(String id, Item item, String sellerID, Instant createdAt) {
        super(id, createdAt);
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        this.item = item;
        this.sellerID = sellerID;
        this.status = AuctionStatus.OPEN;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Tạo Auction rỗng để DAO hydrate dữ liệu từ database.
     */
    public Auction() {

    }

    @Override
    /**
     * Precondition: Auction đã tồn tại.
     * Postcondition: Method hiện trả chuỗi rỗng.
     */
    public String printInfo() {
        return "";
    }

    /**
     * Precondition: Auction đã tồn tại.
     * Postcondition: Method trả về Item đã gắn, hoặc null nếu chưa hydrate.
     */
    public Item getItem() {
        return item;
    }

    /**
     * Precondition: Auction đã tồn tại.
     * Postcondition: Method trả về seller id lưu trong auction.
     */
    public String getSellerID() {
        return sellerID;
    }

    /**
     * Precondition: item, auctionStartTime và auctionEndTime đã có dữ liệu, trừ khi status là
     * CANCELED hoặc PAID.
     * Postcondition: Áp dụng rule trạng thái theo thời gian rồi trả về status hiện tại.
     * NOTE: Getter này có side effect vì updateStatusByTime() có thể ghi xuống database.
     */
    public AuctionStatus getStatus() {
        updateStatusByTime();
        return status;
    }

    public AuctionStatus getStoredStatus() {
        return status;
    }
    /**
     * Precondition: item có auctionStartTime và auctionEndTime.
     * Postcondition: status thành FINISHED sau giờ kết thúc, RUNNING sau giờ bắt đầu, hoặc OPEN
     * trước giờ bắt đầu. Thay đổi RUNNING/FINISHED được lưu xuống auction_items.status.
     * Method không trả về giá trị.
     * NOTE: CANCELED và PAID là trạng thái kết thúc, không tự động đổi.
     */
    public void updateStatusByTime() {
        if (item == null) {
            return;
        }
        Instant now = Instant.now();

        // 1. Nếu đấu giá đã bị hủy hoặc đã thanh toán thì không tự động đổi nữa
        if (this.status == AuctionStatus.CANCELLED || this.status == AuctionStatus.PAID || this.status == null) {
            return;
        }

        // 2. Kiểm tra mốc kết thúc (Ưu tiên kiểm tra kết thúc trước)
        if (now.isAfter(item.getAuctionEndTime()) || now.equals(item.getAuctionEndTime())) {
            if (this.status != AuctionStatus.FINISHED) {
                this.status = AuctionStatus.FINISHED;
                // Gọi DAO để đồng bộ xuống Database ngay lập tức
                DAOAuction_Items.getInstance().Update_Status(this, this.item, AuctionStatus.FINISHED);
            }
        }
        // 3. Kiểm tra mốc bắt đầu
        else if (now.isAfter(item.getAuctionStartTime()) || now.equals(item.getAuctionStartTime())) {
            if (this.status == AuctionStatus.OPEN) {
                this.status = AuctionStatus.RUNNING;
                // Đồng bộ trạng thái RUNNING xuống Database
                DAOAuction_Items.getInstance().Update_Status(this, this.item, AuctionStatus.RUNNING);
            }
        }
        // 4. Mặc định vẫn là OPEN nếu chưa tới giờ
        else {
            this.status = AuctionStatus.OPEN;
        }
    }

    /**
     * Precondition: Auction đã tồn tại.
     * Postcondition: Method trả về label hiển thị khi chưa có bidder dẫn đầu.
     */
    public String getDefaultBidder() {
        return "Người bán";
    }

    /**
     * Precondition: Auction đã tồn tại.
     * Postcondition: Method trả về username đang dẫn đầu, hoặc null nếu chưa có bid.
     */
    public String getLeadingBidder() {
        return leadingBidder;
    }

    /**
     * Precondition: Auction đã tồn tại.
     * Postcondition: Method trả về view chỉ đọc của bidHistory.
     * NOTE: Code gọi method này phải copy list trước khi thêm BidTransaction.
     */
    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    /**
     * Ý nghĩa của Collections.unmodifiableList
     * Dòng code này trả về một "Read-only list" (Danh sách chỉ đọc).
     *
     * Nếu bạn cố gắng đọc dữ liệu: Mọi thứ vẫn hoạt động bình thường. Bạn có thể dùng vòng lặp để xem danh sách hoặc lấy một phần tử ra.
     *
     * Nếu bạn cố gắng thay đổi dữ liệu: Nếu một bên thứ ba gọi phương thức này và cố gắng thực hiện các thao tác như .add() (thêm), .remove() (xóa), hoặc .clear() (xóa hết), chương trình sẽ ném ra ngoại lệ UnsupportedOperationException và dừng lại ngay lập tức.
     */
    /**
     * Precondition: status là trạng thái vòng đời mới.
     * Postcondition: Thay thế status trong bộ nhớ.
     * Method không trả về giá trị.
     */
    public void setStatus(AuctionStatus status){
        this.status = status;
    }
    /**
     * Precondition: leadingBidder là username của người đang đặt giá cao nhất.
     * Postcondition: Thay thế leading bidder trong bộ nhớ.
     * Method không trả về giá trị.
     */
    public void setLeadingBidder(String leadingBidder){
        this.leadingBidder = leadingBidder;
    }

    /**
     * Precondition: idItem là giá trị id_item trong database.
     * Postcondition: Cập nhật itemId.
     * Method không trả về giá trị.
     */
    public void setItemId(long idItem) {
        this.itemId = idItem;
    }

    /** Khóa sản phẩm trong bảng auction_items (cột id_item); dùng khi hydrate từ DB mà chưa gắn {@link Item}. */
    /**
     * Precondition: Auction đã tồn tại.
     * Postcondition: Method trả về id item trong database gắn với auction này.
     */
    public long getItemId() {
        return itemId;
    }

    /**
     * Precondition: item là Item thuộc auction này.
     * Postcondition: Auction được gắn với Item truyền vào.
     * Method không trả về giá trị.
     */
    public void setItem(Item item) {
        this.item = item;
    }

    /**
     * Precondition: history chứa các BidTransaction theo thứ tự thời gian.
     * Postcondition: Thay thế reference bidHistory trong bộ nhớ bằng list truyền vào.
     * Method không trả về giá trị.
     */
    public void setBidHistory(List history) {
        this.bidHistory = history;
    }
    /**
     * Precondition: Auction đã tồn tại, item có giá khởi điểm (buyNowPrice/reservePrice).
     * Postcondition: Trả về giá cao nhất hiện tại của phiên đấu giá.
     * Nếu chưa có ai đặt giá, trả về giá khởi điểm (getCurrentPrice) của chính Item đó.
     */
    public double getCurrentPrice() {
        if (bidHistory == null || bidHistory.isEmpty()) {
            // Nếu chưa có ai đặt giá, lấy giá mặc định ban đầu từ Item
            return (item != null) ? item.getCurrentHighestPrice() : 0.0;
        }
        // Nếu đã có lịch sử, lấy mức giá của lượt giao dịch cuối cùng (cao nhất)
        BidTransaction highestBid = bidHistory.get(bidHistory.size() - 1);
        return highestBid.getAmount();
    }

    /**
     * Hàm Overriding phương thức equals mặc định của Java.
     * Giúp hệ thống Client (ArrayList.indexOf) có thể so sánh và tìm kiếm chính xác
     * hai đối tượng Auction hoặc Item dựa trên ID lưu trữ thay vì so sánh địa chỉ ô nhớ.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Auction other = (Auction) obj;
        return this.itemId == other.itemId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(itemId);
    }
}
