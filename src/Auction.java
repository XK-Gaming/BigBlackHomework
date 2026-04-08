import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity{
    private Item item;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;
    private final List<BidTransaction> bidTransactions;

    public Auction(String id, Item item, LocalDateTime startTime, LocalDateTime endTime){
        super(id);
        if (item == null){
            throw new IllegalArgumentException("Auction required item");
        }
        if (startTime == null || endTime == null || startTime.isAfter(endTime)){
            throw new IllegalArgumentException("Aution time is not valid");
        }
        this.item = item;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AuctionStatus.OPEN; //sau khi khoi tao, trang thai ban dau la OPEN
        this.bidTransactions = new ArrayList<>();
        updateStatusByTime();
    }

    public Item getItem() {
        return item;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public AuctionStatus getStatus() {
        updateStatusByTime();
        return status;
    }

    public List<BidTransaction> getBidTransactions() {
        return new ArrayList<>(bidTransactions);
        // tra ve ban sao de ko bi chinh sua tu ben ngoai
    }

    public double getCurrentPrice(){
        if (bidTransactions.isEmpty()){
            return item.getStartingPrice();
        }
        return bidTransactions.get(bidTransactions.size() -1).getAmount();
        //lấy amount của bidtransaction mới nhất
    }

    public Bidder getWinner(){
        if (getStatus() != AuctionStatus.FINISHED || bidTransactions.isEmpty()){
            return null;
        }
        return (bidTransactions.get(bidTransactions.size() -1)).getBidder();
        //lay ra bidder cua transaction cuoi cung
    }

    public void startAuction(){
        if (status == AuctionStatus.OPEN){
            status = AuctionStatus.RUNNING;
        }
    }

    public void finishAuction(){
        if (status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING){
            status = AuctionStatus.FINISHED;
        }
    }

    public void cancelAuction(){
        if (status != AuctionStatus.FINISHED){
            status = AuctionStatus.CANCELED;
        }
    }

    public void placeBid(String bidTransactionId, Bidder bidder, double amount){
        updateStatusByTime();
        if (bidTransactionId == null || bidTransactionId.isBlank()){
            throw new IllegalArgumentException("Bid Transaction id is required");
        }
        if (bidder == null){
            throw new IllegalArgumentException("Bidder invalid");
        }
        if (status != AuctionStatus.RUNNING){
            throw new IllegalArgumentException("Can only bid in RUNNING stage");
        }
        if (amount <= getCurrentPrice()){
            throw new IllegalArgumentException("bidder must bid higher than current price");
        }
        bidTransactions.add(new BidTransaction(bidTransactionId, bidder, amount));
    }

    private void updateStatusByTime(){
        LocalDateTime now = LocalDateTime.now();
        if (status == AuctionStatus.OPEN && !now.isBefore(startTime) && now.isBefore(endTime)) {
            status = AuctionStatus.RUNNING;
        }
        if ((status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING) && !now.isBefore(endTime)) {
            status = AuctionStatus.FINISHED;
        }
    }

    @Override
    public String printInfo() {
        updateStatusByTime();
        String winner = getWinner() == null ? "Chua co" : getWinner().getUsername();
        return "Auction " + getId()
                + "\nItem: " + item.printInfo()
                + "\nStatus: " + status
                + "\nCurrent price: " + String.format("%,.0f", getCurrentPrice())
                + "\nWinner: " + winner
                + "\nTotal bids: " + bidTransactions.size();
    }
}