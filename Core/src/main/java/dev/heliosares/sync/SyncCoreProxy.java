package dev.heliosares.sync;

import dev.heliosares.sync.net.DisconnectReason;
import dev.heliosares.sync.net.SyncServer;

import java.util.UUID;

public interface SyncCoreProxy extends SyncCore {
    boolean hasWritePermission(String user);

    void callConnectEvent(String server, String ip, boolean readOnly);

    void callDisconnectEvent(String server, DisconnectReason reason);

    boolean isOnline(UUID uuid);
    @Override
    SyncServer getSync();
}
