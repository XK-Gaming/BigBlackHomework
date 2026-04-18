package model.User;



public class User {
    private String username;
    private String password;
    private String name;
    private String email;
    private UserRole role;

    public User(String username, String password, String name, String email, UserRole role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return email;
    }

    public void setAddress(String address) {this.email = address;}

    public UserRole getRole() {
        return role;
    }
    public String getRole_toString() {
        if (role.equals(UserRole.ADMIN)) {
            return "Admin";
        }
        if (role.equals(UserRole.SELLER)) {
            return "Người bán";
        }
        if (role.equals(UserRole.BIDDER)) {
            return "Người đấu giá";
        }
        return "";
    }


}
