package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.BlobPacket;
import dev.kshl.kshlib.encryption.EncryptionAES;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class P2PClientHandler extends ServerClientHandler {
    private final SyncCore plugin;
    private final SyncClient syncClient;

    P2PClientHandler(SyncCore plugin, SyncServer server, Socket socket) throws IOException {
        super(plugin, server, socket);

        this.plugin = plugin;
        this.syncClient = (SyncClient) plugin.getSync();
    }

    @Override
    public void handshake() throws IOException, GeneralSecurityException {
        AtomicBoolean handshakeComplete = new AtomicBoolean();
        plugin.runAsync(() -> {
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (handshakeComplete.get()) return;
            plugin.warning("Handshake timed out for " + getName() + "@" + getIP());
            close();
        });
        setName(new String(readRaw()));
        Boolean hasWritePermission = syncClient.hasWritePermission(getName());
        if (hasWritePermission == null) {
            throw new GeneralSecurityException("Unknown client: " + getName());
        }
        if (!hasWritePermission) {
            throw new GeneralSecurityException("Client does not have write permissions: " + getName());
        }
        String context = UUID.randomUUID().toString().replace("-", "");
        plugin.debug("Sending context=" + context);
        sendRaw(context.getBytes());
        if (!new String(readRaw()).equals("ACK-CTXT")) {
            throw new IOException("Context not acknowledged correctly.");
        }
        BlobPacket blobPacket = new BlobPacket("Sync:" + context, PacketType.P2P_AUTH, new JSONObject());
        SecretKey aes = EncryptionAES.generateRandomKey();
        setEncryption(new EncryptionAES(aes));
        blobPacket.setBlob(aes.getEncoded());
        plugin.getSync().send(getName(), blobPacket);

        if (!new String(readRaw()).equals("ACK-KEY")) {
            throw new IOException("Key exchange not acknowledged correctly.");
        }

        plugin.print("[P2P] Client " + getName() + " connected!");
    }
}
