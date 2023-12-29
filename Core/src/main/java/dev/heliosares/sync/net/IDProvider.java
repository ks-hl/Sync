package dev.heliosares.sync.net;

public class IDProvider {
    public static final short MIN_CONNECTION_ID = (short) 0;
    public static final short MAX_CONNECTION_ID = (short) (999);
    private static final short NUM_CONNECTION_IDS = MAX_CONNECTION_ID - MIN_CONNECTION_ID + 1;

    private static short rollOverConnectionID(short connectionID) {
        while (connectionID < MIN_CONNECTION_ID) connectionID += NUM_CONNECTION_IDS;
        while (connectionID > MAX_CONNECTION_ID) connectionID -= NUM_CONNECTION_IDS;
        return connectionID;
    }

    private final short connectionID;
    private long lastID = 0;

    public IDProvider(short connectionID) {
        this.connectionID = rollOverConnectionID(connectionID);
    }

    public synchronized ID getNextID() {
        final long nextID = lastID++;
        // Place packetID in the higher bits and connectionID in the lower bits
        long id = nextID * 1000 + connectionID;
        return new ID(id, connectionID, nextID);
    }

    public short getConnectionID() {
        return connectionID;
    }

    public static ID parse(long id) {
        return new ID(id, (short) (id % 1000), id / 1000);
    }

    public record ID(long combined, short connectionID, long packetID) {
    }
}
