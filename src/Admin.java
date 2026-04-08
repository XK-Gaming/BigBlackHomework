public class Admin extends User{
    public Admin(String id, String username, String password, String fullname){
        super(id, username, password, fullname);
    }

    @Override
    public String getRole(){
        return "Admin";
    }
}