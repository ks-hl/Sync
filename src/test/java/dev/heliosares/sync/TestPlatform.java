package dev.heliosares.sync;

import dev.heliosares.sync.net.PlayerData;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.*;

public abstract class TestPlatform implements SyncCore {
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(10);
    private final Logger logger;

    public TestPlatform(String name) {
        this.logger = Logger.getLogger(name);
        logger.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$s] [%3$s] %4$s %n";

            @Override
            public synchronized String format(LogRecord lr) {
                return String.format(format, new Date(lr.getMillis()), lr.getLevel().getLocalizedName(), lr.getLoggerName(), lr.getMessage());
            }
        });
        logger.addHandler(handler);
    }

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
        getLogger().log(Level.WARNING, t.getMessage(), t);
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