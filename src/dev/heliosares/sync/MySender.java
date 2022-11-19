package dev.heliosares.sync;

public interface MySender {
    public String getName();

    public void sendMessage(String msg);

    public boolean hasPermission(String node);

    public boolean hasPermissionExplicit(String node);

    public void execute(String command);

    public Object getSender();
}
