package dev.heliosares.sync.net;

public class IDProvider {
    private static final int CONNECTION_ID_BITS = 12;
    public static final short MIN_CONNECTION_ID = (short) -Math.pow(2, CONNECTION_ID_BITS - 1);
    public static final short MAX_CONNECTION_ID = (short) (-1 - MIN_CONNECTION_ID);
    private static final int PACKET_ID_BITS = 64 - CONNECTION_ID_BITS;
    private static final long CONNECTION_ID_MASK = (1L << CONNECTION_ID_BITS) - 1;
    private static final long PACKET_ID_MASK = (1L << PACKET_ID_BITS) - 1;

    private final short connectionID;
    private long lastID = 0;

    public IDProvider(short connectionID) {
        this.connectionID = connectionID;
    }

    public synchronized ID getNextID() {
        return new ID(((long) connectionID & CONNECTION_ID_MASK) << PACKET_ID_BITS | (lastID & PACKET_ID_MASK), connectionID, lastID++);
    }

    public short getConnectionID() {
        return connectionID;
    }

    public static ID parse(long id) {
        short connectionID = (short) ((id >>> PACKET_ID_BITS) & CONNECTION_ID_MASK);
        long packetID = id & CONNECTION_ID_MASK;

        return new ID(id, connectionID, packetID);
    }

    public record ID(long combined, short connectionID, long packetID) {
    }
}
