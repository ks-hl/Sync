package dev.heliosares.sync;

public interface MySender {
    String getName();

    void sendMessage(String msg);

    boolean hasPermission(String node);

    boolean hasPermissionExplicit(String node);

    void execute(String command);

    Object getSender();
}
