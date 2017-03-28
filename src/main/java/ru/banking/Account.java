package ru.banking;

import ru.banking.exceptions.AccountBalanceOverflowException;

import java.util.concurrent.locks.StampedLock;

/**
 * by Anatolii Danilov on 28-3-2017.
 * For simplicity of the exercise we assume that the currency is KRW,
 * currency which doesn't have decimal places
 */
public class Account {

    private String accountNumber;
    private volatile long balance;
    private volatile boolean isBlocked;

    private final StampedLock lock = new StampedLock();


    public Account(String accountNumber) {
        if (accountNumber == null)
            throw new NullPointerException("Please assign non-null account number");

        this.accountNumber = accountNumber;
    }

    public Account initialBalance(long initialBalance) {
        balance = initialBalance;
        return this;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public long getBalance() {
        long stamp = lock.tryOptimisticRead();
        long balance = this.balance;
        if (lock.validate(stamp)) {
            return balance;
        }
        stamp = lock.readLock();
        try {
            return this.balance;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public void withdraw(long amount) throws AccountBalanceOverflowException {
        long before = balance;
        balance -= amount;
        if (balance > before) {
            throw new AccountBalanceOverflowException(accountNumber);
        }
    }

    public void deposit(long amount) throws AccountBalanceOverflowException {
        long before = balance;
        balance += amount;
        if (balance < before) {
            throw new AccountBalanceOverflowException(accountNumber);
        }
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void blockAccount() {
        isBlocked = true;
    }

    public long writeLock() {
        return lock.writeLock();
    }

    public void releaseLock(long stamp) {
        lock.unlockWrite(stamp);
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountNumber='" + accountNumber + '\'' +
                ", isBlocked=" + isBlocked +
                '}';
    }
}
