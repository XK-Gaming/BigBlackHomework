public abstract class User extends Entity {
    private String fullName;
    private String username;
    private String password;

    protected User(String id, String fullName, String username, String password) {
        super(id);
        setFullName(fullName);
        setUsername(username);
        setPassword(password);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank.");
        }
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank.");
        }
        this.username = username;
    }

    public boolean authenticate(String rawPassword) {
        return password.equals(rawPassword);
    }

    protected void setPassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must contain at least 6 characters.");
        }
        this.password = password;
    }

    public abstract String getRole();

    @Override
    public void printInfo() {
        System.out.println("User{id='%s', role='%s', fullName='%s', username='%s'}"
                .formatted(getId(), getRole(), fullName, username));
    }
}
