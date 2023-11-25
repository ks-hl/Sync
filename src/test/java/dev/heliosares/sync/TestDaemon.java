package dev.heliosares.sync;

import dev.heliosares.sync.daemon.SyncDaemon;
import dev.kshl.kshlib.encryption.EncryptionRSA;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.spec.InvalidKeySpecException;

public class TestDaemon extends SyncDaemon {

    public TestDaemon(String name) throws FileNotFoundException, InvalidKeySpecException {
        super(name);
    }

    @Override
    protected File getPrivateKeyOrGen(String name) {
        File file = new File("test/" + name + "/private.key");
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        if (!file.exists()) {
            System.out.println("Key does not exist, regenerating...");
            File publicKeyFile = new File("test/clients/" + name + ".public.key");
            try {
                boolean ignored = file.createNewFile();
                if (!publicKeyFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    publicKeyFile.getParentFile().mkdirs();
                    boolean ignored2 = publicKeyFile.createNewFile();
                }
                EncryptionRSA.RSAPair pair = EncryptionRSA.generate();
                pair.privateKey().write(file);
                pair.publicKey().write(publicKeyFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return file;
    }
}
