package com.plm.locking;

/**
 * Thrown when a user attempts to check-in or otherwise release
 * an item that is currently locked by a different user.
 */
public class NotOwnerException extends RuntimeException {
    public NotOwnerException(String itemId, String requestingUser, String actualOwner) {
        super("User '" + requestingUser + "' cannot release item '" + itemId
                + "' — currently locked by '" + actualOwner + "'");
    }
}
