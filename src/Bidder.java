public class Bidder extends User{
    public Bidder(String id, String username, String password, String fullname){
        super(id, username, password, fullname);
    }

    @Override
    public String getRole(){
        return "Bidder";
    }
}