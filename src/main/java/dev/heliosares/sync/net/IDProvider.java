package dev.heliosares.sync.net;

public class IDProvider {
    private static final int CONNECTION_ID_BITS = 12;
    public static final short MIN_CONNECTION_ID = (short) -Math.pow(2, CONNECTION_ID_BITS - 1);
    public static final short MAX_CONNECTION_ID = (short) (-1 - MIN_CONNECTION_ID);
    private static final int PACKET_ID_BITS = 64 - CONNECTION_ID_BITS;
    private static final long CONNECTION_ID_MASK = (1L << CONNECTION_ID_BITS) - 1;
    private static final long PACKET_ID_MASK = (1L << PACKET_ID_BITS) - 1;

    private static short rollOverConnectionID(short connectionID) {
        while (connectionID > MAX_CONNECTION_ID) connectionID -= (short) Math.pow(2, CONNECTION_ID_BITS);
        return connectionID;
    }

    private final short connectionID;
    private long lastID = 0;

    public IDProvider(short connectionID) {
        this.connectionID = rollOverConnectionID(connectionID);
    }

    public synchronized ID getNextID() {
        final long nextID = lastID++;
        long id = (((long) connectionID & CONNECTION_ID_MASK) << PACKET_ID_BITS) | (nextID & PACKET_ID_MASK);
        return new ID(id, connectionID, nextID);
    }

    public short getConnectionID() {
        return connectionID;
    }

    public static ID parse(long id) {
        short connectionID = rollOverConnectionID((short) ((id >>> PACKET_ID_BITS) & CONNECTION_ID_MASK));
        long packetID = id & PACKET_ID_MASK;

        return new ID(id, connectionID, packetID);
    }

    public record ID(long combined, short connectionID, long packetID) {
    }
}
