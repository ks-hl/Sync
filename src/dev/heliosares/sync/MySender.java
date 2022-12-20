package dev.heliosares.sync;

public interface MySender {
    void sendMessage(String msg);

    boolean hasPermission(String node);

    boolean hasPermissionExplicit(String node);

    void execute(String command);

    String getName();

    Object getSender();
}
