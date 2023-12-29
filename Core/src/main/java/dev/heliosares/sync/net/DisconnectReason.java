package dev.heliosares.sync.net;

import dev.heliosares.sync.net.SyncServer;

public enum DisconnectReason {
    /**
     * The protocol version of the server and the protocol version of the client are different.
     */
    PROTOCOL_MISMATCH,
    /**
     * The key provided by the client is either invalid or not authorized.
     */
    UNAUTHORIZED,
    /**
     * An {@link java.io.IOException} occurred during handshake.
     */
    ERROR_DURING_HANDSHAKE,
    /**
     * An {@link Exception} occurred after handshake.
     */
    ERROR_AFTER_HANDSHAKE,
    /**
     * The client terminated its connection.
     */
    CLIENT_DISCONNECT,
    /**
     * The server terminated its connection, likely during plugin shutdown and/or overall proxy shutdown.
     */
    SERVER_DROPPED_CLIENT,
    /**
     * The client did not send any packets for more than {@link SyncServer#getTimeoutMillis()} milliseconds.
     */
    TIMEOUT
}
