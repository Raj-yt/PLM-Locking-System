# PLM Locking System

A Java-based **multi-user check-in/check-out and locking system** that models a core concurrency problem found in Product Lifecycle Management (PLM) systems.

The system allows multiple users to work with shared PLM artifacts such as parts, CAD files, and BOM nodes while ensuring that only one user can hold an item for editing at a time. It also supports blocking checkout, non-blocking checkout, administrative force-unlock, and automatic recovery of stale locks.

---

## Overview

In a multi-user PLM environment, several users may try to access or modify the same engineering item simultaneously.

A simple approach might be to acquire a `ReentrantLock` during checkout and release it during check-in. However, this design is incorrect for a long-running checkout workflow because Java's `ReentrantLock` must be unlocked by the **same thread that acquired it**.

In a real application:

```text
User A
   |
   | Check-out request
   ↓
Thread A
   |
   | establishes ownership
   ↓
Item is checked out by User A
   |
   | User edits the item
   | (could take minutes or hours)
   ↓
User A
   |
   | Check-in request
   ↓
Thread B
```

Thread B cannot directly unlock a `ReentrantLock` acquired by Thread A.

This project solves the problem by separating:

1. **Logical ownership** — which user currently owns the checkout.
2. **Synchronization** — a short-lived mutex used to safely read and modify the checkout state.

The synchronization lock is therefore **not held during the entire editing session**.

---

## Key Features

### 1. Multi-User Check-Out

A user can check out an item for editing.

```text
User A → Check-out → Item X
                      ↓
                 Owned by A
```

Another user attempting to check out the same item can either wait or receive an immediate rejection depending on the API used.

---

### 2. Blocking Check-Out

A user can wait for an item to become available.

```text
User A → Checkout → Item X
                     🔒
User B → Checkout → waits
                     |
                     ↓
User A → Check-in
                     |
                     ↓
User B → acquires checkout
```

A `Condition` is used to allow waiting threads to sleep and be notified when the item becomes available.

---

### 3. Non-Blocking Check-Out

The system also supports an immediate checkout attempt.

If another user already owns the item:

```text
tryCheckOut()
      ↓
Item unavailable
      ↓
return immediately
```

The caller does not have to wait.

---

### 4. Ownership Validation

Only the user who currently owns an item can check it back in.

For example:

```text
Item X → owned by User A

User B → check-in
       ↓
NotOwnerException
```

This prevents another user from accidentally releasing someone else's checkout.

---

### 5. Administrative Force Unlock

An administrator can force-release an item when a user abandons a session or becomes unavailable.

```text
Admin
  |
  ↓
forceUnlock(Item X)
  |
  ↓
Item becomes available
```

This models an administrative recovery mechanism commonly required in long-running systems.

---

### 6. Stale-Lock Recovery

The project includes a background reaper that checks for items held beyond a configured maximum duration.

```text
Item checked out
       ↓
maximum duration exceeded
       ↓
Background reaper
       ↓
checkout released
```

This models recovery from situations such as a crashed or abandoned client session.

---

### 7. Thread-Safe Item Management

`LockManager` maintains the registered items while `ItemLock` manages synchronization and checkout state.

The system uses Java concurrency utilities such as:

* `ReentrantLock`
* `Condition`
* `ConcurrentHashMap`
* `ScheduledExecutorService`

---

### 8. Concurrency Stress Testing

`ConcurrencyStressTest` creates **50 concurrent threads** that repeatedly attempt to operate on the same item.

The test verifies that the locking mechanism maintains mutual exclusion even under concurrent access.

---

## Architecture

```text
                         LockManager
                              |
             +----------------+----------------+
             |                |                |
        Register Items    Check-Out/In     Force Unlock
             |                |                |
             +----------------+----------------+
                              |
                          ItemLock
                              |
             +----------------+----------------+
             |                |                |
       ReentrantLock       Condition        Ownership
                                              |
                                           ownerId
                              |
                          Item
                              |
                     id / name / revision
                              |
                              ↓
                       PLM Artifact
```

### Components

#### `Item.java`

Represents a lockable PLM artifact.

Contains:

* Item ID
* Item name
* Revision counter

---

#### `ItemLock.java`

The core concurrency component.

Responsible for:

* synchronization
* checkout ownership
* blocking checkout
* non-blocking checkout
* check-in
* timeout handling
* condition signaling

This class separates the **logical checkout state** from the short-lived synchronization mutex.

---

#### `LockManager.java`

Provides the public service layer.

Responsible for:

* registering items
* checking items out
* checking items in
* force-unlocking items
* managing stale-lock recovery

---

#### `NotOwnerException.java`

Raised when a user attempts to check in an item that belongs to another user.

---

#### `ItemNotLockedException.java`

Raised when an operation expects an item to be checked out but the item is currently available.

---

#### `Demo.java`

Demonstrates the main functionality:

* normal checkout
* concurrent checkout attempts
* blocked requests
* wrong-owner check-in
* administrative force unlock

---

#### `ConcurrencyStressTest.java`

Tests the locking implementation under concurrent access using 50 threads.

---

## Concurrency Design

The most important design decision is **not holding the synchronization lock throughout the user's editing session**.

### Incorrect approach

```text
checkOut()
    ↓
lock()

User edits for a long time

checkIn()
    ↓
unlock()
```

This fails when checkout and check-in occur on different threads.

### Approach used in this project

```text
                 ReentrantLock
                      |
              protects state
                      |
          +-----------+-----------+
          |                       |
      ownerId              checkout state
          |                       |
       User A                 available/
                              checked out
```

The `ReentrantLock` protects the state transitions, while `ownerId` represents the actual long-lived logical ownership.

Therefore:

```text
Thread A
   ↓
check-out
   ↓
ownerId = User A
   ↓
release synchronization lock


        User A edits


Thread B
   ↓
check-in
   ↓
verify ownerId == User A
   ↓
clear ownership
   ↓
signal waiting threads
```

This avoids relying on the same Java thread to perform both operations.

---

## Technologies Used

* **Java**
* `ReentrantLock`
* `Condition`
* `ConcurrentHashMap`
* `ScheduledExecutorService`
* Java Exception Handling
* Multithreading
* Concurrency Testing

---

## Project Structure

```text
plm-locking-system/
│
├── src/
│   └── com/
│       └── plm/
│           └── locking/
│               ├── Item.java
│               ├── ItemLock.java
│               ├── LockManager.java
│               ├── NotOwnerException.java
│               ├── ItemNotLockedException.java
│               ├── Demo.java
│               └── ConcurrencyStressTest.java
│
└── README.md
```

---

## Requirements

* Java JDK 8 or later
* Command line / terminal

No external libraries are required.

---

## Build

Compile all Java source files:

```bash
javac -d out src/com/plm/locking/*.java
```

This generates compiled `.class` files inside the `out` directory.

---

## Run the Demo

```bash
java -cp out com.plm.locking.Demo
```

The demo walks through the main PLM locking workflow, including concurrent requests and administrative operations.

---

## Run the Concurrency Test

```bash
java -cp out com.plm.locking.ConcurrencyStressTest
```

The stress test launches 50 concurrent threads against the same item and verifies that mutual exclusion is maintained.

---

## Example Workflow

```text
Register Item
     ↓
User A checks out Item X
     ↓
Item X is owned by User A
     ↓
User B attempts checkout
     ↓
User B waits / receives rejection
     ↓
User A checks in Item X
     ↓
Waiting users are notified
     ↓
Item X becomes available
```

---

## Design Goals

This project focuses on understanding and implementing:

* Thread-safe state management
* Mutual exclusion
* Java concurrency primitives
* Blocking and non-blocking operations
* Ownership validation
* Timeout-based recovery
* Concurrent testing
* Separation of synchronization from logical state
* Service-oriented software design

---

## What This Project Demonstrates

The project is a small-scale simulation rather than a production PLM platform. Its primary goal is to demonstrate how **concurrency and resource ownership can be modeled safely in a multi-user system**.

The design specifically explores the difference between:

```text
Synchronization
      vs.
Logical Ownership
```

and how that distinction affects the implementation of long-running check-out/check-in workflows.

---

## Future Improvements

Potential extensions include:

* REST API layer
* Persistent database storage
* Authentication and authorization
* Distributed locking for multiple server instances
* Audit logs for checkout/check-in operations
* Item version conflict detection
* User/session management
* Event-based notifications
* Integration with a web-based PLM interface

---

## Author

**Rajwardhan Shinde**

Computer Science & Engineering
Walchand College of Engineering, Sangli
