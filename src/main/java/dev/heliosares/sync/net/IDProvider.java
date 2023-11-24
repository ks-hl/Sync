package dev.heliosares.sync.net;

public class IDProvider {
    private static long lastID = 0;

    protected static synchronized long getNextID() {
        long out = System.nanoTime();
        if (out <= lastID) out = lastID + 1;
        if (out == Long.MIN_VALUE) out++;
        return lastID = out;
    }
}
