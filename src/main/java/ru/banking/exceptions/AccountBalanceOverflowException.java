package ru.banking.exceptions;

/**
 * by Anatolii Danilov on 28-3-2017.
 */
public class AccountBalanceOverflowException extends Exception {
    public AccountBalanceOverflowException(String account) {
        super("Transaction leads to an overflow of an account balanace. " + account);
    }
}
