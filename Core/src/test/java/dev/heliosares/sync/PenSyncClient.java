package dev.heliosares.sync;

import dev.heliosares.sync.net.SocketConnection;
import dev.heliosares.sync.net.SyncClient;
import dev.kshl.kshlib.encryption.EncryptionRSA;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class PenSyncClient extends SyncClient {
    public PenSyncClient(SyncCore plugin, EncryptionRSA encryption) {
        super(plugin, encryption);
    }

    @Override
    protected void handshake(SocketConnection connection) throws IOException, GeneralSecurityException {
        while (true) connection.readRaw();
    }
}
