package ru.banking.exceptions;

/**
 * by Anatolii Danilov on 28-3-2017.
 */
public class AccountNotFoundException extends Exception {
    public AccountNotFoundException(String accountNumber) {
        super("Account: " + accountNumber + " is not configured");
    }
}
