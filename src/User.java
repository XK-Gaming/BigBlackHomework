public abstract class User extends Entity{
    private String username;
    private String password;
    private String fullname;

    protected User(String id, String username, String password, String fullname){
        super(id);
        setUsername(username);
        setPassword(password);
        setFullname(fullname);
    }

    public String getUsername(){
        return username;
    }

    public void setUsername(String username){
        if (username == null || username.isBlank()){
            throw new IllegalArgumentException("Username must not be blank.");
        }
        this.username = username.trim();
    }

    public void setPassword(String password){
        if (password == null || password.isBlank()){
            throw new IllegalArgumentException("Password must not be blank.");
        }
        this.password = password;
    }

    public boolean checkPassword(String password){
        return this.password.equals(password);
    }
    //kiem tra password khi log in

    public String getFullname(){
        return fullname;
    }

    public void setFullname(String fullname){
        if (fullname == null || fullname.isBlank()){
            throw new IllegalArgumentException("fullname must not be blank.");
        }
        this.fullname = fullname;
    }

    public abstract String getRole();

    @Override
    public final String printInfo(){
        return getRole() + "|" + getUsername() + "|" + getFullname();
    }
}
