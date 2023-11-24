package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.utils.EncryptionAES;
import dev.heliosares.sync.utils.EncryptionDH;
import dev.heliosares.sync.utils.EncryptionRSA;

import javax.crypto.SecretKey;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.security.*;
import java.util.UUID;
import java.util.function.Consumer;

public class ServerClientHandler extends SocketConnection implements Runnable {

    private final SyncCoreProxy plugin;
    private final SyncServer server;
    private boolean writePermission;

    public ServerClientHandler(SyncCoreProxy plugin, SyncServer server, Socket socket) throws IOException, InvalidKeyException {
        super(socket);
        this.plugin = plugin;
        this.server = server;
        setEncryption(new EncryptionAES(EncryptionAES.generateKey(), EncryptionAES.generateIv()));
    }

    @Override
    public void run() {
        try {
            AlgorithmParameters params = EncryptionDH.generateParameters();
            KeyPair pair = EncryptionDH.generate(params);
            sendRaw(params.getEncoded());
            sendRaw(pair.getPublic().getEncoded());

            PublicKey clientKeyDH = EncryptionDH.getPublicKey(readRaw());
            SecretKey keyDH = EncryptionDH.combine(pair.getPrivate(), clientKeyDH);
            UUID user_uuid = UUID.fromString(new String(EncryptionDH.decrypt(keyDH, readRaw())));
            EncryptionRSA clientRSA = server.getEncryptionFor(user_uuid);
            if (clientRSA == null) {
                throw new InvalidKeyException();
            }
            sendRaw(EncryptionDH.encrypt(keyDH, clientRSA.encrypt(getEncryption().encodeKey())));
            if (!new String(read()).equals("ACK")) {
                // Tests that the client has the decrypted AES key
                throw new InvalidKeyException();
            }
            send(clientRSA.getUser().getBytes());
            setName(clientRSA.getUser());
            writePermission = server.hasWritePermission(getName());
            server.updateClientsWithServerList();
            server.getUserManager().sendPlayers(getName(), null);

        } catch (GeneralSecurityException e) {
            plugin.print("Client failed to authenticate. " + getIP());
            close();
            server.remove(this);
            if (plugin.debug()) {
                plugin.print(e);
            }
            return;
        } catch (IOException e) {
            plugin.print("Error during handshake.");
            plugin.print(e);
            close();
            server.remove(this);
            return;
        }
        plugin.print(getName() + " connected on IP " + getIP() + (!writePermission ? ", read-only" : ""));
        while (isConnected()) {
            try {
                Packet packet = listen();
                if (packet == null) {
                    plugin.warning("Null packet received");
                    continue;
                }
                if (!writePermission && packet.getPacketId() != Packets.KEEPALIVE.id && packet.getPacketId() != Packets.PLAYER_DATA.id) {
                    plugin.warning(getName() + " tried to send a packet but does not have write permission: " + packet);
                    continue;
                }
                packet.setOrigin(getName());
                if (packet.getPacketId() != Packets.KEEPALIVE.id) {
                    final String forward = packet.getForward();
                    if (packet.getForward() != null) {
                        packet.setForward(getName());
                        Consumer<String> sendToServer = serverName -> server.send(serverName, packet);
                        if (forward.equalsIgnoreCase("all")) {
                            server.getServers().stream().filter(name -> !name.equalsIgnoreCase(getName())).forEach(sendToServer);
                        } else {
                            sendToServer.accept(forward);
                        }
                    }
                    if (forward == null || forward.equalsIgnoreCase("all")) {
                        server.getEventHandler().execute(getName(), packet);
                    }
                }
            } catch (NullPointerException | SocketException | EOFException e1) {
                if (plugin.debug()) {
                    plugin.print(e1);
                }
                break;
            } catch (Exception e) {
                plugin.print(e);
            }
        }
        if (getName() != null) {
            plugin.print(getName() + " disconnected.");
        }
        close();
        server.remove(this);
    }
}
