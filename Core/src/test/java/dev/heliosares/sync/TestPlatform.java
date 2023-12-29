package dev.heliosares.sync;

import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.utils.CustomLogger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class TestPlatform implements SyncCore {
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(100);
    private final Logger logger;

    public TestPlatform(String name) {
        this.logger = CustomLogger.getLogger(name);
    }

    @Override
    public void newThread(Runnable run) {
        scheduler.submit(run);
    }

    @Override
    public void runAsync(Runnable run) {
        scheduler.submit(run);
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
    public void print(String message, Throwable t) {
        if (message == null) message = "";
        else message += ": ";
        message += t.getMessage();
        getLogger().log(Level.WARNING, message, t);
    }

    @Override
    public void warning(String msg) {
        getLogger().warning(msg);
    }

    @Override
    public void print(String msg) {
        getLogger().info(msg);
    }

    protected Logger getLogger() {
        return logger;
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