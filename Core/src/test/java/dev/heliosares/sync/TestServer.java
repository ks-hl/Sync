package dev.heliosares.sync;

import dev.heliosares.sync.net.DisconnectReason;
import dev.heliosares.sync.net.SyncServer;
import dev.kshl.kshlib.encryption.EncryptionRSA;

import java.io.File;
import java.io.FileNotFoundException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TestServer extends TestPlatform implements SyncCoreProxy {
    SyncServer syncNetCore = new SyncServer(this);

    public TestServer(String name) {
        super(name);
    }

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
    public boolean hasWritePermission(String user) {
        return true;
    }

    @Override
    public void callConnectEvent(String server, String ip, boolean readOnly) {
        getLogger().info(String.format("ConnectEvent: server=%s, ip=%s, readOnly=%s", server, ip, readOnly));
    }

    @Override
    public void callDisconnectEvent(String server, DisconnectReason reason) {
        getLogger().info(String.format("DisconnectEvent: server=%s, reason=%s", server, reason));
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return false;
    }

    @Override
    public SyncServer getSync() {
        return syncNetCore;
    }
}
