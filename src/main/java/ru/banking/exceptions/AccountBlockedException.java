package ru.banking.exceptions;

/**
 * by Anatolii Danilov on 28-3-2017.
 */
public class AccountBlockedException extends Exception {
    public AccountBlockedException(String accountNumber) {
        super("Account: " + accountNumber + " is blocked");
    }
}
