package com.plm.locking;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the check-out state for a single item.
 *
 * IMPORTANT DESIGN NOTE:
 * A naive implementation might try to represent "item is checked out" by
 * having the checking-out thread call lock() and having check-in call
 * unlock(). That's broken for this use case: a ReentrantLock may only be
 * unlocked by the thread that locked it (IllegalMonitorStateException
 * otherwise), but in a real system the request that checks an item back in
 * almost never runs on the same thread as the request that checked it out
 * (different HTTP request, different worker thread, minutes or hours later).
 *
 * So here the ReentrantLock + Condition are only ever held for the brief
 * instant needed to read/mutate the "who owns this item" state and to
 * park/wake threads that are waiting for it to free up. The actual
 * check-out (the thing that lasts arbitrarily long, across threads) is
 * just a plain field, guarded by that brief critical section — not the
 * lock itself.
 */
class ItemLock {
    private final String itemId;
    private final ReentrantLock mutex = new ReentrantLock();
    private final Condition available = mutex.newCondition();

    private String ownerId = null;
    private Instant checkedOutAt = null;

    ItemLock(String itemId) {
        this.itemId = itemId;
    }

    /**
     * Attempts to check the item out to {@code userId}. If it's already
     * checked out, blocks (releasing the mutex while waiting) for up to
     * {@code waitMillis} for it to become free.
     *
     * @return true if the check-out succeeded, false if it timed out
     */
    boolean checkOut(String userId, long waitMillis) throws InterruptedException {
        mutex.lock();
        try {
            long remainingNanos = TimeUnit.MILLISECONDS.toNanos(waitMillis);
            while (ownerId != null) {
                if (remainingNanos <= 0) {
                    return false;
                }
                remainingNanos = available.awaitNanos(remainingNanos);
            }
            ownerId = userId;
            checkedOutAt = Instant.now();
            return true;
        } finally {
            mutex.unlock();
        }
    }

    /** Non-blocking variant: succeeds immediately or fails immediately. */
    boolean tryCheckOut(String userId) {
        mutex.lock();
        try {
            if (ownerId != null) {
                return false;
            }
            ownerId = userId;
            checkedOutAt = Instant.now();
            return true;
        } finally {
            mutex.unlock();
        }
    }

    /** Releases the item. Only the current owner may do this. */
    void checkIn(String userId) {
        mutex.lock();
        try {
            if (ownerId == null) {
                throw new ItemNotLockedException(itemId);
            }
            if (!ownerId.equals(userId)) {
                throw new NotOwnerException(itemId, userId, ownerId);
            }
            ownerId = null;
            checkedOutAt = null;
            available.signalAll();
        } finally {
            mutex.unlock();
        }
    }

    /** Admin override: releases the item regardless of who holds it. */
    String forceUnlock() {
        mutex.lock();
        try {
            String previousOwner = ownerId;
            ownerId = null;
            checkedOutAt = null;
            available.signalAll();
            return previousOwner;
        } finally {
            mutex.unlock();
        }
    }

    boolean isLocked() {
        mutex.lock();
        try {
            return ownerId != null;
        } finally {
            mutex.unlock();
        }
    }

    String getOwner() {
        mutex.lock();
        try {
            return ownerId;
        } finally {
            mutex.unlock();
        }
    }

    Instant getCheckedOutAt() {
        mutex.lock();
        try {
            return checkedOutAt;
        } finally {
            mutex.unlock();
        }
    }
}
