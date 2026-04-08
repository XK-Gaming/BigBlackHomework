public class Art extends Item{
    private String artist;

    public Art(String id, Seller seller, String name, String description, double startingPrice, String artist){
        super(id, seller, name, description, startingPrice);
        setArtist(artist);
    }

    public void setArtist(String artist){
        if (artist == null || artist.isBlank()){
            throw new IllegalArgumentException("Artist must not be blank.");
        }
        this.artist = artist;
    }

    @Override
    public String getItemType(){
        return "Art";
    }

    @Override
    public String printInfo(){
        return getItemType() + " | " + getName()
                + " | Giá khởi điểm: " + formatPrice(getStartingPrice())
                + " | Artist: " + artist
                + " | Seller: " + getSeller().getUsername();
    }
}
