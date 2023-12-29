package dev.heliosares.sync.bungee.event;

public class ClientDisconnectedEvent extends ClientEvent {

    private final Reason reason;

    public ClientDisconnectedEvent(String clientName, Reason reason) {
        super(clientName);
        this.reason = reason;
    }

    public enum Reason {
        PROTOCOL_MISMATCH, UNAUTHORIZED, ERROR_DURING_HANDSHAKE, CLIENT, TIMEOUT
    }

    public Reason getReason() {
        return reason;
    }
}
