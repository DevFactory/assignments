package ru.banking;

/**
 * by Anatolii Danilov on 28-3-2017.
 */
interface SecurityDepartment {
    default boolean checkIsNeeded(long amount) {
        return amount > 50_000;
    }

    boolean isFraud(Account from, Account to, long amount) throws InterruptedException;
}
