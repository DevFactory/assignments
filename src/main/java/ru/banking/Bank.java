package ru.banking;

import ru.banking.exceptions.AccountBalanceOverflowException;
import ru.banking.exceptions.AccountBlockedException;
import ru.banking.exceptions.AccountNotFoundException;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * by Anatolii Danilov on 28-3-2017.
 */
public class Bank {

    private Map<String, Account> allAccounts = new ConcurrentHashMap<>();

    private SecurityDepartment securityDepartment = new RandomSecurityDepartment();

    public boolean addAccount(Account account) {
        Account alreadyExistingAccount = allAccounts.putIfAbsent(account.getAccountNumber(), account);
        return alreadyExistingAccount == null;
    }

    public long getBalance(String accountNumber) throws AccountNotFoundException {
        Account account = getAccount(accountNumber);
        return account.getBalance();
    }


    public void transfer(String accountNumberFrom, String accountNumberTo, long amount) throws AccountNotFoundException,
            InterruptedException, AccountBlockedException, AccountBalanceOverflowException {
        if (accountNumberFrom.equals(accountNumberTo)) {
            return; // useless operation
        }

        Account accountFrom = getAccount(accountNumberFrom);
        Account accountTo = getAccount(accountNumberTo);

        long stampFrom;
        long stampTo;
        // order locking to avoid deadlocks
        if (accountNumberFrom.compareTo(accountNumberTo) > 0) {
            stampTo = accountTo.writeLock();
            stampFrom = accountFrom.writeLock();
        } else {
            stampFrom = accountFrom.writeLock();
            stampTo = accountTo.writeLock();
        }

        try {
            if (accountFrom.isBlocked())
                throw new AccountBlockedException(accountFrom.getAccountNumber());

            if (accountTo.isBlocked())
                throw new AccountBlockedException(accountTo.getAccountNumber());

            accountFrom.withdraw(amount);
            accountTo.deposit(amount);

            if (securityDepartment.checkIsNeeded(amount)) {
                boolean isFraud = isFraud(accountFrom, accountTo, amount);
                if (isFraud) {
                    accountFrom.blockAccount();
                    accountTo.blockAccount();
                    log("Fraud is detected, accounts [" + accountFrom + ", " + accountTo + "] are blocked. " +
                            "Client is not notified.");
                }
            }
        } finally {
            accountFrom.releaseLock(stampFrom);
            accountTo.releaseLock(stampTo);
        }


    }

    private void log(String message) {
        System.out.println(message);
    }

    private Account getAccount(String accountNumber) throws AccountNotFoundException {
        Account account = allAccounts.get(accountNumber);
        if (account == null) {
            throw new AccountNotFoundException(accountNumber);
        }
        return account;
    }

    private synchronized boolean isFraud(Account accountFrom, Account accountTo, long amount) throws InterruptedException {
        return securityDepartment.isFraud(accountFrom, accountTo, amount);
    }


    public void setSecurityDepartment(SecurityDepartment securityDepartment) {
        this.securityDepartment = securityDepartment;
    }


    private static class RandomSecurityDepartment implements SecurityDepartment {
        private final Random random = new Random();

        @Override
        public boolean isFraud(Account from, Account to, long amount) throws InterruptedException {
            Thread.sleep(1000);
            return random.nextBoolean();
        }
    }
}
