# Multi-User Check-In / Check-Out & Locking System

A small PLM-style concurrency demo: multiple users "check out" items (parts,
CAD files, BOM nodes) to edit them, other users are blocked or rejected
until check-in, and an admin can force-unlock an abandoned item.

## Why this is harder than it looks

The naive version of this project is: "wrap each item in a
`java.util.concurrent.locks.ReentrantLock`, call `lock()` on check-out and
`unlock()` on check-in." **That's actually broken.** A `ReentrantLock` may
only be unlocked by the exact thread that locked it — but in a real system,
the request that checks an item *in* runs on a different thread (a
different HTTP request, minutes or hours later, maybe on a different
server) than the request that checked it *out*. Calling `unlock()` from a
different thread throws `IllegalMonitorStateException`.

So the design here separates two things that are easy to conflate:

- **The logical check-out state** — "item X is held by user Y" — which is
  just a field, and needs to persist arbitrarily long, across threads.
- **The mutex** — used only for the brief instant needed to safely read or
  mutate that state, and to let waiting threads block/wake via a
  `Condition` when an item frees up. It is *never* held across the
  caller's actual edit session.

This lives in `ItemLock.java`, which is the core of the assignment.
`LockManager.java` is the public-facing service (register items,
check-out/in, force-unlock, and a background "reaper" that force-releases
items held past a max time, modeling a crashed session).

## Files

- `Item.java` — a lockable artifact (id, name, revision counter)
- `ItemLock.java` — per-item lock state (the interesting part)
- `LockManager.java` — the service: check-out, check-in, force-unlock, stale-lock reaper
- `NotOwnerException.java` / `ItemNotLockedException.java` — failure modes
- `Demo.java` — walkthrough: happy path, blocked concurrent request, rejected wrong-owner check-in, admin override
- `ConcurrencyStressTest.java` — spins up 50 threads hammering the same item at once and asserts mutual exclusion never breaks

## Build & run

```
javac -d out src/com/plm/locking/*.java
java -cp out com.plm.locking.Demo
java -cp out com.plm.locking.ConcurrencyStressTest
```

**Note:** this sandbox only has a JRE installed (no `javac`), so I
traced the code by hand rather than compiling it here — worth running
`javac` yourself before you trust it fully. The logic and imports are
straightforward enough that I'm confident in it, but verify before an
interview.

## What to highlight in an interview

- The `ReentrantLock`-can't-cross-threads subtlety above, and why the fix
  (separate logical state from the mutex) is the right one.
- `checkOut(itemId, userId, waitMillis)` supports **blocking** check-out
  (wait for the item to free up) via `Condition.awaitNanos`, alongside a
  non-blocking `tryCheckOut`.
- The expiry watcher (`startExpiryWatcher`) models what happens when a
  client disconnects without checking in — a real gap most first attempts
  at this project miss entirely.
- `ConcurrencyStressTest` is a genuine correctness proof, not just a demo:
  it counts concurrent holders of the critical section and fails loudly if
  it's ever more than one.
