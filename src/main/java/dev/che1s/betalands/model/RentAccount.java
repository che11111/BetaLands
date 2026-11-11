package dev.che1s.betalands.model;

public final class RentAccount {
    private final String playerName;
    private double balance;
    private String debtLandId;

    public RentAccount(String playerName, double balance) {
        this(playerName, balance, null);
    }

    public RentAccount(String playerName, double balance, String debtLandId) {
        this.playerName = playerName;
        this.balance = balance;
        this.debtLandId = debtLandId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void addBalance(double amount) {
        this.balance += amount;
    }

    public String getDebtLandId() {
        return debtLandId;
    }

    public void setDebtLandId(String debtLandId) {
        this.debtLandId = debtLandId;
    }
}
