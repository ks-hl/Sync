package dev.heliosares.sync;

import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncServer;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TestServer implements SyncCoreProxy {
    SyncServer syncNetCore = new SyncServer(this);

    @Override
    public void newThread(Runnable run) {
        TestMain.getScheduler().execute(run);
    }

    @Override
    public void runAsync(Runnable run) {
        TestMain.getScheduler().execute(run);
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
        TestMain.getScheduler().scheduleAtFixedRate(run, delay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void warning(String msg) {
        System.err.println(msg);
    }

    @Override
    public void print(String msg) {
        System.out.println(msg);
    }

    @Override
    public void print(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void debug(String msg) {
        System.out.println(msg);
    }

    @Override
    public void debug(Supplier<String> msgSupplier) {
        System.out.println(msgSupplier.get());
    }

    @Override
    public boolean debug() {
        return true;
    }

    @Override
    public void dispatchCommand(MySender sender, String command) {

    }

    @Override
    public void setDebug(boolean debug) {

    }

    @Override
    public boolean hasWritePermission(String user) {
        return true;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public SyncServer getSync() {
        return syncNetCore;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.DAEMON;
    }

    @Override
    public void onNewPlayerData(PlayerData data) {
    }
}
