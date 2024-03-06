package dev.heliosares.sync.daemon;

import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CustomLogger;
import dev.kshl.kshlib.encryption.EncryptionRSA;

import java.io.File;
import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class SyncDaemon implements SyncCore {

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(10);
    private SyncClient syncClient;

    public File getPublicKeyFile() {
        return new File("DAEMON_NAME.public.key");
    }

    public File getPrivateKeyFile() {
        return new File("private.key");
    }

    public File getServerKeyFile() {
        return new File("server.key");
    }

    public static void main(String[] args) {
        final SyncDaemon plugin;
        Logger logger = CustomLogger.getLogger("Sync");
        plugin = new SyncDaemon() {

            @Override
            public void print(String message, Throwable t) {
                if (message == null) message = "";
                else message += ": ";
                message += t.getMessage();
                logger.log(Level.WARNING, message, t);
            }

            @Override
            public void debug(String msg) {
                print("[Debug] " + msg);
            }

            @Override
            public void warning(String msg) {
                logger.warning(msg);
            }

            @Override
            public void print(String msg) {
                logger.info(msg);
            }
        };

        try {
            plugin.init();
            String command = plugin.connect(args);
            plugin.run(command);
            Thread.sleep(100);
            plugin.close();
        } catch (IllegalArgumentException e) {
            plugin.warning(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            plugin.print("Error while running", e);
            System.exit(2);
        }

        System.exit(0);
    }

    /**
     * Connects to the Sync server
     *
     * @return The unused portion of the args parameter, typically the command to be executed
     */
    public String connect(String... args) throws Exception {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("You must specify arguments (either just a command like `java -jar sync.jar say test` or consume a `-port:<port>` argument at the beginning.)");
        }

        boolean portTerm = false;
        int port = 8001;
        if (args[0].startsWith("-port:")) {
            try {
                port = Integer.parseInt(args[0].substring("-port:".length()));
                portTerm = true;
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid parameter: " + args[0], e);
            }
        }
        connect(port);
        return CommandParser.concat(portTerm ? 1 : 0, args);
    }

    public void init() throws IOException, InvalidKeySpecException {
        if (syncClient != null) throw new IllegalStateException("Already initialized");
        File privateKeyFile = getPrivateKeyFile();
        if (!privateKeyFile.exists()) {
            print("Key does not exist, regenerating...");
            //noinspection ResultOfMethodCallIgnored
            privateKeyFile.getAbsoluteFile().getParentFile().mkdirs();
            boolean ignored = privateKeyFile.createNewFile();
            File publicKeyFile = getPublicKeyFile();
            if (!publicKeyFile.exists()) {
                boolean ignored2 = publicKeyFile.createNewFile();
            }
            EncryptionRSA.RSAPair pair = EncryptionRSA.generate();
            pair.privateKey().write(privateKeyFile);
            pair.publicKey().write(publicKeyFile);
            print("Keys generated successfully. Please copy 'DAEMON_NAME.public.key' to the proxy under 'plugins/Sync/clients/DAEMON_NAME.public.key', renaming 'DAEMON_NAME' to the daemon's name");
        }

        File serverKeyFile = getServerKeyFile();
        if (!serverKeyFile.exists()) {
            warning("Please copy 'server.key' from proxy to Sync folder.");
            throw new IOException();
        }

        this.syncClient = new SyncClient(this, EncryptionRSA.load(privateKeyFile), EncryptionRSA.load(serverKeyFile));
    }

    public void connect(int port) throws Exception {
        if (syncClient == null) throw new IllegalStateException("Not initialized");
        print("Connecting on port " + port + "...");
        getSync().start(null, port).getAndThrow(1, TimeUnit.SECONDS);
        print("Connected as " + getSync().getName());
    }

    public void close() {
        getSync().close();
    }

    public void run(String command) throws IOException {
        if (syncClient == null || !syncClient.isConnected()) throw new IllegalStateException("Not initialized");
        print("Sending: " + command);

        getSync().send(new CommandPacket(command));
        print("Command sent.");
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
}
