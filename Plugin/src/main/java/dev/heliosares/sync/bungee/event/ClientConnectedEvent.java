package dev.heliosares.sync.bungee.event;


public class ClientConnectedEvent extends ClientEvent {
    private final String ip;
    private final boolean readOnly;

    public ClientConnectedEvent(String clientName, String ip, boolean readOnly) {
        super(clientName);
        this.ip = ip;
        this.readOnly = readOnly;
    }

    public String getIp() {
        return ip;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
