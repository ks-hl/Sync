package dev.heliosares.sync.net;

import dev.heliosares.sync.net.packet.Packet;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

public interface SyncNetCore {
    boolean send(Packet packet) throws IOException;

    boolean send(String server, Packet packet) throws IOException;

    @Deprecated
    boolean sendConsumer(String server, Packet packet, Consumer<Packet> consumer) throws IOException;

    boolean send(String server, Packet packet, Consumer<Packet> consumer) throws IOException;

    void close();

    void closeTemporary();

    NetEventHandler getEventHandler();

    Set<String> getServers();

    String getName();

    UserManager getUserManager();

    void start(String host, int port);
}
