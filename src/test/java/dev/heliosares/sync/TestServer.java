package dev.heliosares.sync;

import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncServer;
import dev.heliosares.sync.utils.EncryptionRSA;

import java.io.File;
import java.io.FileNotFoundException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TestServer implements SyncCoreProxy {
    SyncServer syncNetCore = new SyncServer(this);

    public void reloadKeys(boolean print) {
        Set<EncryptionRSA> clientEncryptionRSA = new HashSet<>();
        File clientsDir = new File("test", "clients");
        if (clientsDir.exists()) {
            File[] files = clientsDir.listFiles();
            if (files != null) for (File listFile : files) {
                if (listFile.isFile() && listFile.getName().toLowerCase().endsWith(".public.key")) {
                    try {
                        EncryptionRSA rsa = EncryptionRSA.load(listFile);
                        clientEncryptionRSA.add(rsa);
                        if (print) print("Loaded key for " + rsa.getUser());
                    } catch (FileNotFoundException | InvalidKeySpecException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } else {
            boolean ignored = clientsDir.mkdir();
        }
        getSync().setClientEncryptionRSA(clientEncryptionRSA);
    }

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
        System.err.println("[Server] " + msg);
    }

    @Override
    public void print(String msg) {
        System.out.println("[Server] " + msg);
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
