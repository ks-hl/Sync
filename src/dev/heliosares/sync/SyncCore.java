package dev.heliosares.sync;

import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncNetCore;

import javax.annotation.Nullable;
import java.util.Set;

public interface SyncCore {
    void newThread(Runnable run);

    void runAsync(Runnable run);

    void scheduleAsync(Runnable run, long delay, long period);

    void warning(String msg);

    void print(String msg);

    void print(Throwable t);

    void debug(String msg);

    boolean debug();

    MySender getSender(String name);

    void dispatchCommand(MySender sender, String command);

    void setDebug(boolean debug);

    enum PlatformType {
        SPIGOT, BUNGEE, DAEMON
    }

    boolean isAsync();

    SyncNetCore getSync();

    PlatformType getPlatformType();

    public Set<PlayerData> createNewPlayerDataSet();
}
