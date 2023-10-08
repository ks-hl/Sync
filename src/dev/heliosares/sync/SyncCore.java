package dev.heliosares.sync;

import dev.heliosares.sync.net.SyncNetCore;

import java.util.function.Supplier;

public interface SyncCore {
    void newThread(Runnable run);

    void runAsync(Runnable run);

    void scheduleAsync(Runnable run, long delay, long period);

    void warning(String msg);

    void print(String msg);

    void print(Throwable t);

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

}
