package dev.heliosares.sync.daemon;

import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.*;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.EncryptionRSA;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.security.spec.InvalidKeySpecException;
import java.util.Set;

public class SyncDaemon implements SyncCore {

    private static SyncDaemon instance;

    public SyncDaemon() {
        instance = this;
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("You must specify arguments (either just a command like `java -jar sync.jar say test` or with a `-port:<port>` argument at the beginning.)");
            return;
        }
        int port = 8001;
        boolean portTerm = false;
        if (args[0].startsWith("-port:")) {
            try {
                port = Integer.parseInt(args[0].substring("-port:".length()));
                portTerm = true;
            } catch (NumberFormatException e) {
                System.err.println("Invalid parameter: " + args[0]);
                System.exit(1);
                return;
            }
        }
        String command = CommandParser.concat(portTerm ? 1 : 0, args);
        System.out.println("Sending: " + command);


        File keyFile = new File("public.key");
        if (!keyFile.exists()) {
            System.err.println("Key file does not exist. Please copy it from the proxy.");
            return;
        }

        SyncClient sync;
        try {
            sync = new SyncClient(new SyncDaemon(), EncryptionRSA.load(keyFile));
        } catch (FileNotFoundException | InvalidKeySpecException e) {
            System.err.println("Failed to load key file. Ensure it was correctly copied from the proxy.");
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            return;
        }

        try {
            sync.start(null, port);
            while (!sync.isConnected() || sync.getName() == null) {
                //noinspection BusyWait
                Thread.sleep(10);
            }
            sync.send(new Packet(null, Packets.COMMAND.id, new JSONObject().put("command", command)));
            Thread.sleep(1000);
            sync.close();
        } catch (Exception e1) {
            System.err.println("Unable to connect");
            //noinspection CallToPrintStackTrace
            e1.printStackTrace();
            System.exit(2);
            return;
        }
        System.out.println("Command sent.");
        System.exit(0);
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
    public void warning(String msg) {
        System.err.println(msg);
    }

    @Override
    public void print(String msg) {
        System.out.println(msg);
    }

    @Override
    public void print(Throwable t) {
        //noinspection CallToPrintStackTrace
        t.printStackTrace();
    }

    @Override
    public void debug(String msg) {
        print(msg);
    }

    @Override
    public boolean debug() {
        return true;
    }

    @Override
    public MySender getSender(String name) {
        return null;
    }

    @Override
    public void dispatchCommand(MySender sender, String command) {
    }

    @Override
    public void setDebug(boolean debug) {
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
    }

    @Override
    public SyncNetCore getSync() {
        return null;
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
    public Set<PlayerData> createNewPlayerDataSet() {
        throw new UnsupportedOperationException();
    }
}
