package dev.heliosares.sync;

import dev.heliosares.sync.net.SyncServer;
import dev.heliosares.sync.utils.EncryptionRSA;

import java.io.File;
import java.io.FileNotFoundException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashSet;
import java.util.Set;

public class TestServer extends TestPlatform implements SyncCoreProxy {
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
    public void warning(String msg) {
        System.err.println("[Server] " + msg);
    }

    @Override
    public void print(String msg) {
        System.out.println("[Server] " + msg);
    }

    @Override
    public boolean hasWritePermission(String user) {
        return true;
    }

    @Override
    public SyncServer getSync() {
        return syncNetCore;
    }
}
