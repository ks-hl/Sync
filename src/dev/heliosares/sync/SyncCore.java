package dev.heliosares.sync;

import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncNetCore;

import java.util.List;

public interface SyncCore {
    void runAsync(Runnable run);

    void scheduleAsync(Runnable run, long delay, long period);

    void warning(String msg);

    void print(String msg);

    void print(Throwable t);

    void debug(String msg);

    boolean debug();

    MySender getSender(String name);

    void dispatchCommand(MySender sender, String command);

    SyncNetCore getSync();

    List<PlayerData> getPlayers();

    void setDebug(boolean debug);
}
