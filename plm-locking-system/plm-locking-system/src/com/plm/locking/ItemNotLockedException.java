package com.plm.locking;

/** Thrown when a check-in or unlock is attempted on an item that has no active lock. */
public class ItemNotLockedException extends RuntimeException {
    public ItemNotLockedException(String itemId) {
        super("Item '" + itemId + "' is not currently checked out — nothing to release");
    }
}
