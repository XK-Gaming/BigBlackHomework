import java.util.Locale;

public abstract class Item extends Entity{
    private String name;
    private String description;
    private double startingPrice;
    private Seller seller;

    protected Item(String id, Seller seller, String name, String description, double startingPrice){
        super(id);
        setSeller(seller);
        setName(name);
        setDescription(description);
        setStartingPrice(startingPrice);
    }

    public Seller getSeller(){
        return seller;
    }

    public void setSeller(Seller seller){
        if (seller == null){
            throw new IllegalArgumentException("san pham phai co Seller");
        }
        this.seller = seller;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        if (name == null || name.isBlank()){
            throw new IllegalArgumentException("name must not be blank.");
        }
        this.name = name.trim();
    }

    public void setDescription(String description){
        if (description == null || description.isBlank()){
            throw new IllegalArgumentException("description must not be blank.");
        }
        this.description = description.trim();
    }

    public double getStartingPrice(){
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice){
        if (startingPrice <= 0){
            throw new IllegalArgumentException("starting price must be larger than 0");
        }
        this.startingPrice = startingPrice;
    }

    public abstract String getItemType();

    protected String formatPrice(double price) {
        return String.format(Locale.GERMANY, "%,.0f", price);
    }
    //in dinh dang tien ( ngan cach phan nghin bang dau ".")

    public abstract String printInfo();
}
