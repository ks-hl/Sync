package dev.heliosares.sync.net;

public class IDProvider {
    private static long lastID = 0;

    protected static long getNextID() {
        long out = System.currentTimeMillis() * 1000L;
        if (out <= lastID) out = lastID + 1;
        return lastID = out;
    }
}
