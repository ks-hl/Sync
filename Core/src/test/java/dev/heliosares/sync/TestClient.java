package dev.heliosares.sync;

import dev.heliosares.sync.net.SyncClient;
import dev.kshl.kshlib.encryption.EncryptionRSA;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.spec.InvalidKeySpecException;

public class TestClient extends TestPlatform {
    private final SyncClient syncNetCore;
    private final String name;

    public TestClient(String name, EncryptionRSA serverRSA) throws InvalidKeySpecException {
        this(name, true, null, serverRSA);
    }

    public TestClient(String name, boolean implKey, SyncClient.CreatorFunction clientCreator, EncryptionRSA serverRSA) throws InvalidKeySpecException {
        super(name);
        if (clientCreator == null) clientCreator = SyncClient::new;
        this.name = name;
        File file = new File("test/" + name + "/private.key");
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        if (!file.exists()) {
            System.out.println("Key does not exist, regenerating...");
            File publicKeyFile = new File("test/clients/" + name + ".public.key");
            try {
                boolean ignored = file.createNewFile();
                if (implKey && !publicKeyFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    publicKeyFile.getParentFile().mkdirs();
                    boolean ignored2 = publicKeyFile.createNewFile();
                }
                EncryptionRSA.RSAPair pair = EncryptionRSA.generate();
                pair.privateKey().write(file);
                if (implKey) pair.publicKey().write(publicKeyFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            syncNetCore = clientCreator.create(this, EncryptionRSA.load(file), serverRSA);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SyncClient getSync() {
        return syncNetCore;
    }
}
