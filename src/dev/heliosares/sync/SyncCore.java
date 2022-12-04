package dev.heliosares.sync;

import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncNetCore;

import javax.annotation.Nullable;
import java.util.List;

public interface SyncCore {
    void newThread(Runnable run);

    void runAsync(Runnable run);

    void scheduleAsync(Runnable run, long delay, long period);

    boolean isAsync();

    void warning(String msg);

    void print(String msg);

    void print(Throwable t);

    void debug(String msg);

    boolean debug();

    MySender getSender(String name);

    void dispatchCommand(MySender sender, String command);

    SyncNetCore getSync();

    @Nullable
    List<PlayerData> getPlayers();

    void setDebug(boolean debug);

    PlatformType getPlatformType();

    enum PlatformType {
        SPIGOT, BUNGEE, DAEMON
    }
}
