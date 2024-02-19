package dev.heliosares.sync;

import dev.heliosares.sync.net.IDProvider;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncNetCore;

import java.util.function.Supplier;

public interface SyncCore {
    void newThread(Runnable run);

    void runAsync(Runnable run);

    void scheduleAsync(Runnable run, long delay, long period);

    void scheduleAsync(Runnable run, long delay);

    void warning(String msg);

    void print(String msg);

    void print(String message, Throwable t);

    void debug(String msg);

    void debug(Supplier<String> msgSupplier);

    boolean debug();

    void dispatchCommand(MySender sender, String command);

    void setDebug(boolean debug);

    enum PlatformType {
        SPIGOT, BUNGEE, DAEMON
    }

    boolean isAsync();

    SyncNetCore getSync();

    PlatformType getPlatformType();

    void onNewPlayerData(PlayerData data);
}
