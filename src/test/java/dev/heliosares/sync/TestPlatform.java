package dev.heliosares.sync;

import dev.heliosares.sync.net.PlayerData;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class TestPlatform implements SyncCore {
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(10);

    @Override
    public void newThread(Runnable run) {
        scheduler.execute(run);
    }

    @Override
    public void runAsync(Runnable run) {
        scheduler.execute(run);
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
        scheduler.scheduleAtFixedRate(run, delay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void scheduleAsync(Runnable run, long delay) {
        scheduler.schedule(run, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void print(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void debug(String msg) {
        print(msg);
    }

    @Override
    public void debug(Supplier<String> msgSupplier) {
        print(msgSupplier.get());
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
    public boolean isAsync() {
        return true;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.DAEMON;
    }

    @Override
    public void onNewPlayerData(PlayerData data) {
    }
}