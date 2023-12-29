package dev.heliosares.sync.bungee.event;

import net.md_5.bungee.api.plugin.Event;

public abstract class ClientEvent extends Event {
    private final String clientName;

    ClientEvent(String clientName) {
        this.clientName = clientName;
    }

    public String getClientName() {
        return clientName;
    }
}
