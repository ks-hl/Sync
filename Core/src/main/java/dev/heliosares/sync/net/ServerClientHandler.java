package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.kshl.kshlib.encryption.CodeGenerator;
import dev.kshl.kshlib.encryption.EncryptionAES;
import dev.kshl.kshlib.encryption.EncryptionDH;
import dev.kshl.kshlib.encryption.EncryptionRSA;

import javax.crypto.SecretKey;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.ProviderException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class ServerClientHandler extends SocketConnection implements Runnable {
    public static final String PROTOCOL_VERSION = SyncAPI.PROTOCOL_VERSION;
    private static final Map<String, Short> connectionIDs = new HashMap<>();
    private static short lastConnectionID = 0;
    private final SyncCore plugin;
    private final SyncServer server;
    private int p2pPort;
    protected boolean writePermission;
    protected short connectionID;

    ServerClientHandler(SyncCore plugin, SyncServer server, Socket socket) throws IOException {
        super(plugin, socket);
        this.plugin = plugin;
        this.server = server;
        setEncryption(new EncryptionAES(EncryptionAES.generateRandomKey()));
    }

    protected void handshake() throws GeneralSecurityException, IOException {
        String debugKey = getIP().substring(getIP().indexOf(":") + 1);
        Consumer<String> debug = s -> plugin.debug("[" + debugKey + " Handshake] " + s);
        AlgorithmParameters params = EncryptionDH.generateParameters();
        KeyPair pair = EncryptionDH.generate(params);
        debug.accept("Sending DH parameters");
        sendRaw(params.getEncoded());
        sendRaw(pair.getPublic().getEncoded());

        PublicKey clientKeyDH = EncryptionDH.getPublicKey(readRaw());
        debug.accept("Received DH parameters");
        SecretKey keyDH = EncryptionDH.combine(pair.getPrivate(), clientKeyDH);
        UUID user_uuid = UUID.fromString(new String(EncryptionDH.decrypt(keyDH, readRaw())));
        debug.accept("Received RSA ID " + user_uuid);
        EncryptionRSA clientRSA = server.getEncryptionFor(user_uuid);
        if (clientRSA == null) {
            throw new InvalidKeyException("No key matching provided");
        }
        debug.accept("Sending AES key");
        sendRaw(EncryptionDH.encrypt(keyDH, clientRSA.encrypt(getEncryption().encodeKey())));

        final String nonce = "NCE-" + CodeGenerator.generateSecret(32, true, true, true);
        debug.accept("Sending nonce " + nonce);
        send(nonce.getBytes());
        if (!new String(clientRSA.decrypt(read().decrypted())).equals("ACK-" + nonce)) {
            // Tests that the client has the decrypted AES key and possesses the private RSA key
            throw new InvalidKeyException("Invalid acknowledgement");
        }
        debug.accept("Nonce acknowledged");

        byte[] myVersion = PROTOCOL_VERSION.getBytes();
        byte[] otherVersion = read().decrypted();
        debug.accept("Other protocol is " + new String(otherVersion) + ", sending my protocol v" + PROTOCOL_VERSION);
        send(myVersion);
        if (!Arrays.equals(otherVersion, myVersion)) {
            plugin.warning("Mismatched protocol versions, I'm on " + PROTOCOL_VERSION + ", client is on " + new String(otherVersion) + ", dropping");
            close();
            server.remove(this);
            callDisconnectEvent(DisconnectReason.PROTOCOL_MISMATCH);
            return;
        }
        debug.accept("Sending name " + clientRSA.getUser());
        setName(clientRSA.getUser());
        send(getName().getBytes());
        connectionID = connectionIDs.computeIfAbsent(clientRSA.getUser(), s -> ++lastConnectionID);
        debug.accept("Sending connection ID=" + connectionID);
        send(new byte[]{(byte) (connectionID >> 8), (byte) (connectionID)});
        p2pPort = ByteBuffer.wrap(read().decrypted()).getInt();
        writePermission = server.hasWritePermission(getName());
        server.updateClientsWithServerList();
        server.getUserManager().sendPlayers(getName(), null);
    }

    @Override
    public void run() {
        try {
            handshake();
        } catch (GeneralSecurityException | ProviderException e) {
            plugin.print("Client failed to authenticate. " + getIP() + (plugin.debug() && e.getMessage() != null ? (", " + e.getMessage()) : ""));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            close();
            server.remove(this);
            callDisconnectEvent(DisconnectReason.UNAUTHORIZED);
            return;
        } catch (IOException e) {
            plugin.print("Error during handshake.", e);
            close();
            server.remove(this);
            callDisconnectEvent(DisconnectReason.ERROR_DURING_HANDSHAKE);
            return;
        }

        if (plugin instanceof SyncCoreProxy syncCoreProxy) {
            syncCoreProxy.callConnectEvent(getName(), getIP(), !writePermission);
        }


        plugin.print(getName() + " connected on IP " + getIP() + (!writePermission ? ", read-only" : ""));
        DisconnectReason disconnectReason = null;
        while (isConnected()) {
            try {
                Packet packet = listen();
                if (packet.getType() == PacketType.KEEP_ALIVE) continue;

                packet.setOrigin(getName());
                if (!writePermission && packet.getType() != PacketType.PLAYER_DATA) {
                    plugin.warning(getName() + " tried to send a packet but does not have write permission: " + packet);
                    continue;
                }
                if (packet.getResponseID().connectionID() != connectionID) {
                    plugin.warning(getName() + " tried to send a packet with the wrong connectionID: " + packet);
                    continue;
                }
                final String forward = packet.getForward();
                plugin.runAsync(() -> {
                    try {
                        if (forward != null) {
                            packet.setForward(null);
                            Consumer<String> sendToServer = serverName -> server.send(serverName, packet);
                            if (forward.equalsIgnoreCase("all")) {
                                server.getServers().stream().filter(name -> !name.equalsIgnoreCase(getName())).forEach(sendToServer);
                            } else {
                                sendToServer.accept(forward);
                            }
                        }
                        if (forward == null || forward.equalsIgnoreCase("all")) {
                            if (!packet.isResponse() && packet instanceof PingPacket pingPacket) {
                                Packet resp = pingPacket.createResponse();
                                resp.assignResponseID(plugin.getSync().getIDProvider());
                                try {
                                    send(resp, null, 0, null);
                                } catch (IOException e) {
                                    plugin.print("Error while sending ping response", e);
                                }
                            }
                            server.getEventHandler().execute(getName(), packet);
                        }
                    } catch (Throwable t) {
                        plugin.print("Error handling packet async", t);
                    }
                });
            } catch (Exception e1) {
                if (e1 instanceof EOFException || (e1 instanceof SocketException socketException && "Socket closed".equals(socketException.getMessage()))) {
                    disconnectReason = isClosed() ? DisconnectReason.SERVER_DROPPED_CLIENT : DisconnectReason.CLIENT_DISCONNECT;
                    plugin.print(String.format(isClosed() ? "Server dropped client '%s'" : "Client '%s' disconnected", getName()));
                } else {
                    disconnectReason = DisconnectReason.ERROR_AFTER_HANDSHAKE;
                    plugin.print("Error from client " + getName() + ", disconnecting", e1);
                }
                break;
            }
        }
        callDisconnectEvent(Objects.requireNonNullElse(disconnectReason, DisconnectReason.SERVER_DROPPED_CLIENT));
        if (getName() != null && disconnectReason == null) {
            plugin.print(getName() + " disconnected.");
        }
        close();
        server.remove(this);
    }

    protected void callDisconnectEvent(DisconnectReason reason) {
        if (plugin instanceof SyncCoreProxy syncCoreProxy) {
            syncCoreProxy.callDisconnectEvent(getName(), reason);
        }
    }

    public int getP2PPort() {
        return p2pPort;
    }
}
