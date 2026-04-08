public class Seller extends User{
    public Seller(String id, String username, String password, String fullname){
        super(id, username, password, fullname);
    }

    @Override
    public String getRole(){
        return "Seller";
    }
}