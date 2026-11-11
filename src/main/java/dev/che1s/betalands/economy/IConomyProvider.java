package dev.che1s.betalands.economy;

import com.iConomy.iConomy;
import com.iConomy.system.Account;
import com.iConomy.system.Holdings;

final class IConomyProvider {
    boolean isReady() {
        return iConomy.Accounts != null;
    }

    boolean hasAccount(String playerName) {
        return iConomy.hasAccount(playerName);
    }

    boolean hasEnough(String playerName, double amount) {
        Holdings holdings = holdings(playerName);
        return holdings != null && holdings.hasEnough(amount);
    }

    double getBalance(String playerName) {
        Holdings holdings = holdings(playerName);
        return holdings == null ? 0.0D : holdings.balance();
    }

    void withdraw(String playerName, double amount) {
        Holdings holdings = holdings(playerName);
        if (holdings == null) {
            throw new IllegalStateException("iConomy account not found");
        }
        holdings.subtract(amount);
    }

    void credit(String playerName, double amount) {
        Holdings holdings = holdings(playerName);
        if (holdings == null) {
            throw new IllegalStateException("iConomy account not found");
        }
        holdings.add(amount);
    }

    private Holdings holdings(String playerName) {
        Account account = iConomy.getAccount(playerName);
        return account == null ? null : account.getHoldings();
    }
}
