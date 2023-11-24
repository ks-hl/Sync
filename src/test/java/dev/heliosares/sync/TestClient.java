package dev.heliosares.sync;

import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.utils.EncryptionRSA;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TestClient implements SyncCore {
    private final SyncClient syncNetCore;

    public TestClient(String name) throws InvalidKeySpecException {
        File file = new File("test/" + name + "/private.key");
        if (!file.exists()) {
            System.out.println("Key does not exist, regenerating...");
            File publicKeyFile = new File("test/clients/" + name + ".public.key");
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
        }

        try {
            syncNetCore = new SyncClient(this, EncryptionRSA.load(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
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
    public boolean isAsync() {
        return true;
    }

    @Override
    public SyncClient getSync() {
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
