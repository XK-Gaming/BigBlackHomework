public class Admin extends User {
    public Admin(String id, String fullName, String username, String password) {
        super(id, fullName, username, password);
    }

    @Override
    public String getRole() {
        return "Admin";
    }
}
