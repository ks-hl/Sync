package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.kshl.kshlib.encryption.EncryptionAES;
import dev.kshl.kshlib.encryption.EncryptionDH;
import dev.kshl.kshlib.encryption.EncryptionRSA;

import javax.crypto.SecretKey;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.ProviderException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ServerClientHandler extends SocketConnection implements Runnable {
    public static final String PROTOCOL_VERSION = SyncAPI.PROTOCOL_VERSION;
    private static final Map<String, Short> connectionIDs = new HashMap<>();
    private static short lastConnectionID = 0;
    private final SyncCoreProxy plugin;
    private final SyncServer server;

    public ServerClientHandler(SyncCoreProxy plugin, SyncServer server, Socket socket) throws IOException {
        super(plugin, socket);
        this.plugin = plugin;
        this.server = server;
        setEncryption(new EncryptionAES(EncryptionAES.generateRandomKey()));
    }

    @Override
    public void run() {
        short connectionID;
        boolean writePermission;
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
                throw new InvalidKeyException("No key matching provided");
            }
            sendRaw(EncryptionDH.encrypt(keyDH, clientRSA.encrypt(getEncryption().encodeKey())));
            if (!new String(read().decrypted()).equals("ACK")) {
                // Tests that the client has the decrypted AES key
                throw new InvalidKeyException("Invalid key");
            }
            send("ACK".getBytes());
            byte[] myVersion = PROTOCOL_VERSION.getBytes();
            byte[] otherVersion = read().decrypted();
            send(myVersion);
            if (!Arrays.equals(otherVersion, myVersion)) {
                plugin.warning("Mismatched protocol versions, I'm on " + PROTOCOL_VERSION + ", client is on " + new String(otherVersion) + ", dropping");
                close();
                server.remove(this);
                return;
            }
            send(clientRSA.getUser().getBytes());
            connectionID = connectionIDs.computeIfAbsent(clientRSA.getUser(), s -> ++lastConnectionID);
            send(new byte[]{(byte) (connectionID >> 8), (byte) (connectionID)});
            setName(clientRSA.getUser());
            writePermission = server.hasWritePermission(getName());
            server.updateClientsWithServerList();
            server.getUserManager().sendPlayers(getName(), null);

        } catch (GeneralSecurityException | ProviderException e) {
            plugin.print("Client failed to authenticate. " + getIP() + (plugin.debug() && e.getMessage() != null ? (", " + e.getMessage()) : ""));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            close();
            server.remove(this);
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
                if (packet.getType() == PacketType.KEEP_ALIVE) continue;

                packet.setOrigin(getName());
                if (!writePermission && packet.getType() != PacketType.PLAYER_DATA) {
                    plugin.warning(getName() + " tried to send a packet but does not have write permission: " + packet);
                    continue;
                }
                if (!packet.isResponse() && IDProvider.parse(packet.getResponseID()).connectionID() != connectionID) {
                    plugin.warning(getName() + " tried to send a packet with the wrong connectionID: " + packet);
                    continue;
                }
                final String forward = packet.getForward();
                plugin.runAsync(() -> {
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
                        if (packet instanceof PingPacket pingPacket) {
                            try {
                                send(pingPacket.createResponse(), null, 0, null);
                            } catch (IOException e) {
                                plugin.warning("Error while sending ping response");
                                plugin.print(e);
                            }
                        }
                    }
                });
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
