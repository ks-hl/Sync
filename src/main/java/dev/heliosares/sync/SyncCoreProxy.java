package dev.heliosares.sync;

import dev.heliosares.sync.net.SyncServer;

public interface SyncCoreProxy extends SyncCore {
    boolean hasWritePermission(String user);

    @Override
    SyncServer getSync();
}
