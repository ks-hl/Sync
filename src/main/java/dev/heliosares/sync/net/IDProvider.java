package dev.heliosares.sync.net;

public class IDProvider {
    private final short connectionID;
    private long lastID = 0;

    public IDProvider(short connectionID) {
        this.connectionID = connectionID;
    }

    public synchronized long getNextID() {
        if (connectionID < 0) throw new IllegalStateException("Connection ID must be set before generating packet IDs");
        return (((long) connectionID & 0xFFFFL) << 48) | (lastID++ & 0xFFFFFFFFFFFFL);
    }

    public short getConnectionID() {
        return connectionID;
    }

    public static ID parse(long id) {
        short connectionID = (short) ((id >>> 48) & 0xFFFF);
        long packetID = id & 0xFFFFFFFFFFFFL;

        return new ID(id, connectionID, packetID);
    }

    public record ID(long combined, short connectionID, long packetID) {
    }
}
