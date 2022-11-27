package dev.heliosares.sync.net;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

public interface SyncNetCore {
    boolean send(Packet packet) throws IOException, GeneralSecurityException;

    boolean send(String server, Packet packet) throws IOException, GeneralSecurityException;

    NetEventHandler getEventHandler();

    List<String> getServers();

    void close();

    void closeTemporary();

    String getName();

    UserManager getUserManager();
}
