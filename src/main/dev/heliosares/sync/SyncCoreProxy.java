package dev.heliosares.sync;

public interface SyncCoreProxy extends SyncCore {
    boolean hasWritePermission(String user);
}
