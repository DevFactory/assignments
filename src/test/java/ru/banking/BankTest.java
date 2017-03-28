package ru.banking;

import org.junit.Before;
import org.junit.Test;
import ru.banking.exceptions.AccountBalanceOverflowException;
import ru.banking.exceptions.AccountBlockedException;
import ru.banking.exceptions.AccountNotFoundException;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

/**
 * by Anatolii Danilov on 28-3-2017.
 */
public class BankTest {

    private static final int AMOUNT_FOR_CONCURRENCY = 100;
    private Bank bank;

    @Before
    public void setUp() throws Exception {
        bank = new Bank();
    }

    @Test
    public void addAccount() throws Exception {
        String testAccountName = "40810";
        boolean added = bank.addAccount(new Account(testAccountName).initialBalance(100));
        assertTrue("Failed to add an account", added);

        assertEquals(100, bank.getBalance(testAccountName));
        boolean addedSuccessfully = bank.addAccount(new Account(testAccountName).initialBalance(50));
        assertFalse("Shouldn't add an account", addedSuccessfully);
        assertEquals(100, bank.getBalance(testAccountName));
    }

    @Test(expected = NullPointerException.class)
    public void addBadAccount() throws Exception {
        boolean added = bank.addAccount(new Account(null).initialBalance(100));
    }

    @Test(expected = AccountNotFoundException.class)
    public void balanceOfUnexistingAccount() throws Exception {
        bank.getBalance("I am not there yet");
    }

    @Test(expected = AccountNotFoundException.class)
    public void simpleTransferToNonExistingAccount() throws Exception {
        String testAccountNameFrom = "40810";
        bank.addAccount(new Account(testAccountNameFrom).initialBalance(100));
        String testAccountNameTo = "408102";
        bank.transfer(testAccountNameFrom, testAccountNameTo, 50);
    }

    @Test(expected = AccountNotFoundException.class)
    public void simpleTransferFromNonExistingAccount() throws Exception {
        String testAccountNameTo = "40810";
        bank.addAccount(new Account(testAccountNameTo).initialBalance(100));
        String testAccountNameFrom = "408102";
        bank.transfer(testAccountNameFrom, testAccountNameTo, 50);
    }

    @Test
    public void simpleTransfer() throws Exception {
        String from = "40810";
        bank.addAccount(new Account(from).initialBalance(100));

        String to = "4081020";
        bank.addAccount(new Account(to).initialBalance(50));

        assertEquals(100, bank.getBalance(from));
        assertEquals(50, bank.getBalance(to));

        bank.transfer(from, to, 50);

        assertEquals(50, bank.getBalance(from));
        assertEquals(100, bank.getBalance(to));

        bank.transfer(from, to, 150);

        // overdraft is allowed
        assertEquals(-100, bank.getBalance(from));
        assertEquals(250, bank.getBalance(to));

        // why not
        bank.transfer(from, from, 1000);
    }

    @Test(expected = AccountBalanceOverflowException.class)
    public void extremeTransferDeposit() throws Exception {
        String from = "450810";
        bank.addAccount(new Account(from).initialBalance(100));

        String to = "4081020";
        bank.addAccount(new Account(to).initialBalance(50));
        bank.transfer(from, to, Long.MAX_VALUE);
    }

    @Test(expected = AccountBalanceOverflowException.class)
    public void extremeTransferWithdraw() throws Exception {
        String from = "40810";
        bank.addAccount(new Account(from).initialBalance(-100));

        String to = "4081020";
        bank.addAccount(new Account(to).initialBalance(50));
        bank.transfer(from, to, Long.MAX_VALUE);
    }

    @Test
    public void bigTransactionNoFraud() throws Exception {
        bank.setSecurityDepartment(new TolerantSecurityDepartment());

        String from = "40810";
        bank.addAccount(new Account(from).initialBalance(100_000));

        String to = "4081020";
        bank.addAccount(new Account(to).initialBalance(50));
        bank.transfer(from, to, 50_001);

        assertEquals(49_999, bank.getBalance(from));
        assertEquals(50_051, bank.getBalance(to));


        String to3rdParty = "4081030";
        bank.addAccount(new Account(to3rdParty).initialBalance(0));
        bank.transfer(to, to3rdParty, 50_051);
        assertEquals(0, bank.getBalance(to));
        assertEquals(50_051, bank.getBalance(to3rdParty));
    }

    @Test(expected = AccountBlockedException.class)
    public void bigTransactionFraudCheckBlockedWithdraw() throws Exception {
        bank.setSecurityDepartment(new TolerantSecurityDepartment());

        String from = "40810";
        bank.addAccount(new Account(from).initialBalance(1_000_000));

        String to = "4081020";
        bank.addAccount(new Account(to).initialBalance(50));
        bank.transfer(from, to, 700_000);

        assertEquals(300_000, bank.getBalance(from));
        assertEquals(700_050, bank.getBalance(to));
        String to3rdParty = "4081030";
        bank.addAccount(new Account(to3rdParty).initialBalance(0));
        bank.transfer(to, to3rdParty, 700_050);
    }

    @Test(expected = AccountBlockedException.class)
    public void bigTransactionFraudCheckBlockedDeposit() throws Exception {
        bank.setSecurityDepartment(new TolerantSecurityDepartment());

        String from = "40810";
        bank.addAccount(new Account(from).initialBalance(1_000_000));

        String to = "4081020";
        bank.addAccount(new Account(to).initialBalance(50));
        bank.transfer(from, to, 700_000);

        assertEquals(300_000, bank.getBalance(from));
        assertEquals(700_050, bank.getBalance(to));
        String from3rdParty = "4081030";
        bank.addAccount(new Account(from3rdParty).initialBalance(0));
        bank.transfer(from3rdParty, to, 50);
    }

    /**
     *
     */
    private class TolerantSecurityDepartment implements SecurityDepartment {

        @Override
        public boolean isFraud(Account from, Account to, long amount) {
            return amount > 666_666;
        }
    }


    @Test(timeout = 5_000)
    public void deadlockCheck() throws Exception {

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch readyLatch = new CountDownLatch(2);
        String from = "40810";
        bank.addAccount(new Account(from).initialBalance(1_000_000));

        String to = "4081020";
        bank.addAccount(new Account(to).initialBalance(50));

        int tries = 10_000;

        new Thread(() -> executeManyTransfers(from, to, tries, start, readyLatch)).start();

        new Thread(() -> executeManyTransfers(to, from, tries, start, readyLatch)).start();

        start.countDown();
        readyLatch.await();
    }

    private void executeManyTransfers(String from, String to, int tries, CountDownLatch latch, CountDownLatch readyLatch) {
        try {
            latch.await();
            for (int i = 0; i < tries; i++) {
                bank.transfer(from, to, AMOUNT_FOR_CONCURRENCY);
            }
        } catch (AccountNotFoundException
                | InterruptedException
                | AccountBalanceOverflowException
                | AccountBlockedException e) {
            fail("Failed " + e.getMessage());
        } finally {
            readyLatch.countDown();
        }
    }


    @Test
    public void consistentMultipleThreadsTransactions() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch readyLatch = new CountDownLatch(4);
        String one = "1";
        bank.addAccount(new Account(one).initialBalance(1_000_000));

        String two = "2";
        bank.addAccount(new Account(two).initialBalance(50));

        String three = "3";
        bank.addAccount(new Account(three).initialBalance(150));

        int tries = 10_000;
        new Thread(() -> executeManyTransfers(one, two, tries, start, readyLatch)).start();

        new Thread(() -> executeManyTransfers(one, three, tries, start, readyLatch)).start();

        new Thread(() -> executeManyTransfers(two, three, tries, start, readyLatch)).start();

        new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < tries; i++) {
                    bank.getBalance(one);
                    bank.getBalance(two);
                    bank.getBalance(three);
                }
            } catch (AccountNotFoundException | InterruptedException e) {
                fail(e.getMessage());
            }
            readyLatch.countDown();
        }).start();

        start.countDown();
        readyLatch.await();
        assertEquals(1_000_000 - tries * 2 * AMOUNT_FOR_CONCURRENCY, bank.getBalance(one));
        assertEquals(50, bank.getBalance(two));
        assertEquals(150 + tries * 2 * AMOUNT_FOR_CONCURRENCY, bank.getBalance(three));
    }

    @Test(expected = AccountBlockedException.class)
    public void defaultSecurityCheckIsImplemented() throws Exception {
        String from = "40810 - default";
        bank.addAccount(new Account(from).initialBalance(1_000_000));

        String to = "4081020 - default";
        bank.addAccount(new Account(to).initialBalance(50));

        // by a good chance that the default implementation should block accounts
        // and we will fail with an account failed exception
        for (int i = 0; i < 1000; i++) {
            bank.transfer(from, to, 50_001);
        }
    }
}