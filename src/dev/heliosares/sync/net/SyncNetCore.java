package dev.heliosares.sync.net;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface SyncNetCore {
    boolean send(Packet packet) throws IOException;

    boolean send(String server, Packet packet) throws IOException;

    boolean sendConsumer(String server, Packet packet, Consumer<Packet> consumer) throws IOException;


    NetEventHandler getEventHandler();

    List<String> getServers();

    void close();

    void closeTemporary();

    String getName();

    UserManager getUserManager();
}
