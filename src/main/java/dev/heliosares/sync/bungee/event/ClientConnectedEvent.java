package dev.heliosares.sync.bungee.event;

import net.md_5.bungee.api.plugin.Cancellable;

public class ClientConnectedEvent extends ClientEvent implements Cancellable {
    private final String ip;
    private final boolean readOnlyOriginal;
    private boolean readOnly;
    private boolean cancelled;

    public ClientConnectedEvent(String clientName, String ip, boolean readOnly) {
        super(clientName);
        this.ip = ip;
        this.readOnly = this.readOnlyOriginal = readOnly;
    }

    public String getIp() {
        return ip;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        if (readOnlyOriginal && !readOnly) throw new IllegalArgumentException("Can not grant write access from event.");
        this.readOnly = readOnly;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
