package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.BlobPacket;
import dev.kshl.kshlib.encryption.EncryptionAES;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class P2PClient extends SyncClient {
    private final SyncCore plugin;
    private final String partnerName;

    public P2PClient(SyncCore plugin, String partnerName) {
        super(plugin, null, null, false);

        this.plugin = plugin;
        this.partnerName = partnerName;
    }

    @Override
    protected void handshake(SocketConnection connection) throws IOException, GeneralSecurityException {
        connection.sendRaw(plugin.getSync().getName().getBytes());

        String context = new String(connection.readRaw());
        plugin.debug("Received context=" + context);

        CompletableFuture<Void> completable = new CompletableFuture<>();
        plugin.getSync().getEventHandler().registerListener(PacketType.P2P_AUTH, "Sync:" + context, (server, packet) -> {
            if (packet.isResponse()) return;
            if (completable.isDone()) return;
            if (!server.equals(partnerName)) {
                plugin.warning("Received P2P_AUTH packet from the wrong server");
                return;
            }
            if (!(packet instanceof BlobPacket blobPacket)) return;

            connection.setEncryption(new EncryptionAES(blobPacket.getBlob()));
            completable.complete(null);
            plugin.getSync().send(server, packet.createResponse(new JSONObject()));
        });

        connection.sendRaw("ACK-CTXT".getBytes());
        try {
            completable.get(3, TimeUnit.SECONDS);
            if (connection.getEncryption() == null) throw new TimeoutException();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new IOException("AES key exchange timed out");
        }

        connection.send("TEST-KEY".getBytes());
        if (!new String(connection.read().decrypted()).equals("TEST-KEY")) {
            throw new IOException("Key exchange failed.");
        }

        plugin.print("[P2P] Handshake complete!");
    }

    @Override
    public UserManager getUserManager() {
        return plugin.getSync().getUserManager();
    }
}
