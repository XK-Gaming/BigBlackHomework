package model.User;

import java.io.Serializable;

public final class Bidder extends User implements Serializable {
    private static final long serialVersionUID = 1L;
    private double balance;
    public Bidder(String username, String password, String name, String email, double balance) {
        super(username, password, name, email, UserRole.BIDDER);
        this.balance = balance;
    }
    public double getBalance() {
        return balance;
    }
    public void setBalance(double balance) {
        this.balance = balance;
    }
}

