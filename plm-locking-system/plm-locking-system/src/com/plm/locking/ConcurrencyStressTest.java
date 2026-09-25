package com.plm.locking;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hammers a single item with many threads attempting to check it out at
 * once, each of which (if it wins) does a tiny bit of "work" then checks
 * back in. Fails loudly if it ever detects two threads holding the lock
 * simultaneously, and reports how many threads succeeded via blocking
 * wait vs. how many gave up.
 */
public class ConcurrencyStressTest {
    private static final int THREAD_COUNT = 50;
    private static final String ITEM_ID = "CONTENDED-ITEM";

    public static void main(String[] args) throws InterruptedException {
        LockManager manager = new LockManager();
        manager.registerItem(new Item(ITEM_ID, "Heavily Contended Part"));

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREAD_COUNT);
        AtomicInteger holdersInsideCriticalSection = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicBoolean violationDetected = new AtomicBoolean(false);

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            String userId = "user-" + i;
            pool.submit(() -> {
                try {
                    startGate.await(); // all threads fire at once
                    boolean got = manager.checkOut(ITEM_ID, userId, 2000);
                    if (got) {
                        successCount.incrementAndGet();
                        int concurrentHolders = holdersInsideCriticalSection.incrementAndGet();
                        if (concurrentHolders != 1) {
                            violationDetected.set(true);
                            System.out.println("!!! VIOLATION: " + concurrentHolders
                                    + " threads inside critical section at once !!!");
                        }
                        Thread.sleep(5); // simulate doing an edit
                        holdersInsideCriticalSection.decrementAndGet();
                        manager.checkIn(ITEM_ID, userId);
                    } else {
                        timeoutCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        System.out.println("Releasing " + THREAD_COUNT + " threads to contend for " + ITEM_ID + " simultaneously...");
        startGate.countDown();
        doneGate.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        manager.shutdown();

        System.out.println();
        System.out.println("=== Results ===");
        System.out.println("Successful check-outs: " + successCount.get());
        System.out.println("Timed out waiting:     " + timeoutCount.get());
        System.out.println("Mutual exclusion held: " + !violationDetected.get());
        System.out.println("Final lock state:      locked=" + manager.isLocked(ITEM_ID));

        if (violationDetected.get()) {
            System.out.println("\nTEST FAILED — two threads held the lock at once.");
            System.exit(1);
        } else if (successCount.get() != THREAD_COUNT) {
            System.out.println("\nTEST FAILED — expected all " + THREAD_COUNT + " threads to eventually succeed.");
            System.exit(1);
        } else {
            System.out.println("\nTEST PASSED — all " + THREAD_COUNT
                    + " threads serialized correctly with zero double-holds.");
        }
    }
}
