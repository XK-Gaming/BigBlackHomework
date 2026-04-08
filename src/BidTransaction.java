import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BidTransaction extends Entity{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private Bidder bidder;
    private double amount;
    private LocalDateTime bidTime;

    public BidTransaction(String id, Bidder bidder, double amount){
        super(id);
        setBidder(bidder);
        setAmount(amount);
        this.bidTime = LocalDateTime.now();
    }

    public Bidder getBidder() {
        return bidder;
    }

    public void setBidder(Bidder bidder) {
        if (bidder == null) {
            throw new IllegalArgumentException("Bidder is required.");
        }
        this.bidder = bidder;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Bid amount must be greater than 0.");
        }
        this.amount = amount;
    }

    public LocalDateTime getBidTime() {
        return bidTime;
    }

    @Override
    public String printInfo() {
        return bidder.getUsername()
                + " bid " + String.format("%,.0f", amount)
                + " at " + FORMATTER.format(bidTime);
    }
}