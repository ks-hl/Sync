package dev.heliosares.sync.bungee.event;

import dev.heliosares.sync.net.DisconnectReason;

public class ClientDisconnectedEvent extends ClientEvent {

    private final DisconnectReason reason;

    public ClientDisconnectedEvent(String clientName, DisconnectReason reason) {
        super(clientName);
        this.reason = reason;
    }

    public DisconnectReason getReason() {
        return reason;
    }
}
