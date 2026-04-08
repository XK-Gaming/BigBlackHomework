public class Electronics extends Item{
    private String brand;

    public Electronics(String id, Seller seller, String name, String description, double startingPrice, String brand){
        super(id, seller, name, description, startingPrice);
        setBrand(brand);
    }

    public void setBrand(String brand) {
        if (brand == null || brand.isBlank()){
            throw new IllegalArgumentException("brand must not be blank.");
        }
        this.brand = brand;
    }

    @Override
    public String getItemType(){
        return "Electronics";
    }

    @Override
    public String printInfo(){
        return getItemType() + " | " + getName()
                + " | Giá khởi điểm: " + formatPrice(getStartingPrice())
                + " | Brand: " + brand
                + " | Seller: " + getSeller().getUsername();
    }

}
