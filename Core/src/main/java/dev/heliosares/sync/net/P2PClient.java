package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.BlobPacket;
import dev.kshl.kshlib.encryption.EncryptionAES;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class P2PClient extends SyncClient {
    private final SyncCore plugin;
    private final String partnerName;

    public P2PClient(SyncCore plugin, String partnerName) {
        super(plugin, null, false);

        this.plugin = plugin;
        this.partnerName = partnerName;
    }

    @Override
    protected void handshake(SocketConnection connection) throws IOException, GeneralSecurityException {
        connection.sendRaw(plugin.getSync().getName().getBytes());

        String context = new String(connection.readRaw());
        plugin.debug("Received context=" + context);

        CompletableFuture<EncryptionAES> keyCompletableFuture = new CompletableFuture<>();
        plugin.getSync().getEventHandler().registerListener(PacketType.P2P_AUTH, "Sync:" + context, (server, packet) -> {
            if (keyCompletableFuture.isDone()) return;
            plugin.debug("Received auth packet " + packet);
            if (!server.equals(partnerName)) {
                plugin.warning("Received P2P_AUTH packet from the wrong server");
                return;
            }
            if (!(packet instanceof BlobPacket blobPacket)) return;

            keyCompletableFuture.complete(new EncryptionAES(blobPacket.getBlob()));
        });

        connection.sendRaw("ACK-CTXT".getBytes());
        try {
            connection.setEncryption(keyCompletableFuture.get(3, TimeUnit.SECONDS));
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new IOException("AES key exchange timed out");
        }
        connection.send("ACK-KEY".getBytes());

        plugin.print("[P2P] Handshake complete!");
    }

    @Override
    public UserManager getUserManager() {
        return plugin.getSync().getUserManager();
    }
}
