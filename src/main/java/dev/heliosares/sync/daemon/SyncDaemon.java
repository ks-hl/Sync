package dev.heliosares.sync.daemon;

import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.utils.CommandParser;
import dev.kshl.kshlib.encryption.EncryptionRSA;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.*;

public class SyncDaemon implements SyncCore {

    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(10);
    private static SyncDaemon instance;
    private final SyncClient syncClient;
    private final Logger logger;

    public SyncDaemon(String name) throws FileNotFoundException, InvalidKeySpecException {
        instance = this;
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

        syncClient = new SyncClient(this, EncryptionRSA.load(getPrivateKeyOrGen(name)));
    }

    protected File getPrivateKeyOrGen(String name) {
        File file = new File("private.key");
        if (!file.exists()) {
            print("Key does not exist, regenerating...");
            File publicKeyFile = new File("DAEMON_NAME.public.key");
            try {
                boolean ignored = file.createNewFile();
                if (!publicKeyFile.exists()) {
                    boolean ignored2 = publicKeyFile.createNewFile();
                }
                EncryptionRSA.RSAPair pair = EncryptionRSA.generate();
                pair.privateKey().write(file);
                pair.publicKey().write(publicKeyFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            print("Keys generated successfully. Please copy 'DAEMON_NAME.public.key' to the proxy under 'plugins/Sync/clients/DAEMON_NAME.public.key', renaming 'DAEMON_NAME' to the daemon's name");
        }
        return file;
    }

    public static void main(String[] args) {
        final SyncDaemon plugin;
        try {
            plugin = new SyncDaemon("daemon");
        } catch (FileNotFoundException | InvalidKeySpecException e) {
            System.err.println("Failed to load key file.");
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            return;
        }

        try {
            run(plugin, args);
        } catch (Exception e) {
            plugin.print(e);
            System.exit(2);
        }

        System.exit(0);
    }

    public static void run(SyncDaemon plugin, String... args) {
        if (args == null || args.length == 0) {
            System.err.println("You must specify arguments (either just a command like `java -jar sync.jar say test` or consume a `-port:<port>` argument at the beginning.)");
            return;
        }
        int port = 8001;
        boolean portTerm = false;
        if (args[0].startsWith("-port:")) {
            try {
                port = Integer.parseInt(args[0].substring("-port:".length()));
                portTerm = true;
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid parameter: " + args[0], e);
            }
        }

        try {
            plugin.getSync().start(null, port).getAndThrow(1, TimeUnit.SECONDS);
        } catch (Exception e1) {
            throw new RuntimeException("Failed to connect.", e1);
        }
        String command = CommandParser.concat(portTerm ? 1 : 0, args);
        System.out.println("Sending: " + command);
        try {
            plugin.getSync().send(new CommandPacket(command));
            System.out.println("Command sent.");
            Thread.sleep(100);
        } catch (Exception e1) {
            throw new RuntimeException("Failed to send command.", e1);
        }
        plugin.getSync().close();
    }

    public static SyncCore getInstance() {
        return instance;
    }

    @Override
    public void newThread(Runnable run) {
        new Thread(run).start();
    }

    @Override
    public void runAsync(Runnable run) {
        newThread(run);
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
    public void scheduleAsync(Runnable run, long delay, long period) {
        scheduler.scheduleAtFixedRate(run, delay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void scheduleAsync(Runnable run, long delay) {
        scheduler.schedule(run, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public SyncClient getSync() {
        return syncClient;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.DAEMON;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void onNewPlayerData(PlayerData data) {
    }

    @Override
    public void print(Throwable t) {
        getLogger().log(Level.WARNING, t.getMessage(), t);
    }

    @Override
    public void debug(String msg) {
        print("[Debug] " + msg);
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
}
