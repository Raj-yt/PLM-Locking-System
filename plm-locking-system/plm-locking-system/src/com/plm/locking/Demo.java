package com.plm.locking;

public class Demo {
    public static void main(String[] args) throws InterruptedException {
        LockManager manager = new LockManager();
        manager.registerItem(new Item("PART-001", "Bracket Assembly"));
        manager.registerItem(new Item("PART-002", "Housing Cover"));

        System.out.println("=== 1. Basic check-out / check-in ===");
        manager.checkOut("PART-001", "alice", 1000);
        System.out.println("PART-001 locked by: " + manager.getOwner("PART-001"));
        manager.checkIn("PART-001", "alice");
        System.out.println("PART-001 now locked? " + manager.isLocked("PART-001"));
        System.out.println("Revision after edit: " + manager.getItem("PART-001").getRevision());

        System.out.println();
        System.out.println("=== 2. Conflicting check-out (alice holds it, bob tries) ===");
        manager.checkOut("PART-002", "alice", 1000);
        boolean bobGotIt = manager.tryCheckOut("PART-002", "bob");
        System.out.println("Bob's immediate attempt succeeded? " + bobGotIt);

        System.out.println();
        System.out.println("=== 3. Bob waits for alice to finish (blocking check-out) ===");
        Thread bobThread = new Thread(() -> {
            try {
                long start = System.currentTimeMillis();
                boolean got = manager.checkOut("PART-002", "bob", 3000);
                long waited = System.currentTimeMillis() - start;
                System.out.println("Bob acquired PART-002 after waiting " + waited + "ms? " + got);
                if (got) {
                    manager.checkIn("PART-002", "bob");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        bobThread.start();
        Thread.sleep(800); // let bob start waiting
        System.out.println("Alice finishes her edit and checks in...");
        manager.checkIn("PART-002", "alice");
        bobThread.join();

        System.out.println();
        System.out.println("=== 4. Wrong-owner check-in is rejected ===");
        manager.checkOut("PART-001", "alice", 1000);
        try {
            manager.checkIn("PART-001", "mallory");
        } catch (NotOwnerException e) {
            System.out.println("Rejected as expected: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 5. Admin force-unlock ===");
        manager.forceUnlock("PART-001", "admin", "alice's session crashed");
        System.out.println("PART-001 now locked? " + manager.isLocked("PART-001"));

        manager.shutdown();
    }
}
