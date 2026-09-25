package com.plm.locking;

/** A lockable engineering artifact — a CAD part, document, or BOM node. */
public class Item {
    private final String id;
    private final String name;
    private volatile int revision;

    public Item(String id, String name) {
        this.id = id;
        this.name = name;
        this.revision = 1;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getRevision() { return revision; }

    /** Only ever called while the caller holds the item's lock. */
    void bumpRevision() { revision++; }

    @Override
    public String toString() {
        return "Item{id='" + id + "', name='" + name + "', rev=" + revision + "}";
    }
}
