public class Bidder extends User {
    public Bidder(String id, String fullName, String username, String password) {
        super(id, fullName, username, password);
    }

    @Override
    public String getRole() {
        return "Bidder";
    }
}
