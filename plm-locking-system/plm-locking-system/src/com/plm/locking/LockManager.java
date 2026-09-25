package com.plm.locking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Central service for checking items in and out. Thread-safe: many threads
 * may call checkOut/checkIn concurrently on the same or different items.
 */
public class LockManager {
    private final Map<String, Item> items = new ConcurrentHashMap<>();
    private final Map<String, ItemLock> locks = new ConcurrentHashMap<>();

    private ScheduledExecutorService reaper;

    public void registerItem(Item item) {
        items.put(item.getId(), item);
        locks.putIfAbsent(item.getId(), new ItemLock(item.getId()));
    }

    private ItemLock lockFor(String itemId) {
        ItemLock lock = locks.get(itemId);
        if (lock == null) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }
        return lock;
    }

    /** Blocks up to waitMillis for the item to become available, then checks it out. */
    public boolean checkOut(String itemId, String userId, long waitMillis) throws InterruptedException {
        boolean acquired = lockFor(itemId).checkOut(userId, waitMillis);
        if (acquired) {
            log(userId + " checked out " + itemId);
        } else {
            log(userId + " timed out waiting to check out " + itemId);
        }
        return acquired;
    }

    /** Fails immediately instead of waiting if the item is already locked. */
    public boolean tryCheckOut(String itemId, String userId) {
        boolean acquired = lockFor(itemId).tryCheckOut(userId);
        log(acquired
                ? userId + " checked out " + itemId
                : userId + " could not check out " + itemId + " (already locked)");
        return acquired;
    }

    /** Checks the item back in and bumps its revision (an edit was committed). */
    public void checkIn(String itemId, String userId) {
        lockFor(itemId).checkIn(userId);
        Item item = items.get(itemId);
        if (item != null) {
            item.bumpRevision();
        }
        log(userId + " checked in " + itemId);
    }

    /** Admin override — releases the lock no matter who holds it. */
    public void forceUnlock(String itemId, String adminId, String reason) {
        String previousOwner = lockFor(itemId).forceUnlock();
        log(adminId + " force-unlocked " + itemId
                + (previousOwner != null ? " (was held by " + previousOwner + ")" : " (was already free)")
                + " — reason: " + reason);
    }

    public boolean isLocked(String itemId) {
        return lockFor(itemId).isLocked();
    }

    public String getOwner(String itemId) {
        return lockFor(itemId).getOwner();
    }

    public Item getItem(String itemId) {
        return items.get(itemId);
    }

    public List<String> allItemIds() {
        return items.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * Starts a background thread that force-unlocks any item held longer
     * than {@code maxHoldMillis} — models a session/crash timeout so a
     * client that disconnects without checking in doesn't lock an item
     * forever.
     */
    public void startExpiryWatcher(long maxHoldMillis, long pollIntervalMillis) {
        reaper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lock-expiry-watcher");
            t.setDaemon(true);
            return t;
        });
        reaper.scheduleAtFixedRate(() -> {
            Instant cutoff = Instant.now().minus(Duration.ofMillis(maxHoldMillis));
            for (String itemId : locks.keySet()) {
                ItemLock lock = locks.get(itemId);
                Instant checkedOutAt = lock.getCheckedOutAt();
                if (checkedOutAt != null && checkedOutAt.isBefore(cutoff)) {
                    forceUnlock(itemId, "system", "exceeded max hold time of " + maxHoldMillis + "ms");
                }
            }
        }, pollIntervalMillis, pollIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (reaper != null) {
            reaper.shutdownNow();
        }
    }

    private void log(String message) {
        System.out.println("[" + Instant.now() + "] " + message);
    }
}
