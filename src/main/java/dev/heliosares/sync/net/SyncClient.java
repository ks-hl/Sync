package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.heliosares.sync.utils.CompletableException;
import dev.kshl.kshlib.encryption.EncryptionAES;
import dev.kshl.kshlib.encryption.EncryptionDH;
import dev.kshl.kshlib.encryption.EncryptionRSA;

import javax.annotation.Nullable;
import javax.crypto.SecretKey;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SyncClient implements SyncNetCore {
    public static final String PROTOCOL_VERSION = SyncAPI.PROTOCOL_VERSION;
    private final SyncCore plugin;
    private final NetEventHandler eventHandler;
    private final UserManager usermanager;
    private final EncryptionRSA encryptionRSA;
    private SocketConnection connection;
    private boolean closed;
    private int unableToConnectCount = 0;
    private Set<String> servers = new HashSet<>();

    private IDProvider idProvider;
    private boolean handshakeComplete;
    private final CompletableException<Exception> connectedCompletable = new CompletableException<>();

    public SyncClient(SyncCore plugin, EncryptionRSA encryption) {
        this.plugin = plugin;
        this.eventHandler = new NetEventHandler(plugin);
        this.usermanager = new UserManager(plugin, this);
        this.encryptionRSA = encryption;
        eventHandler.registerListener(PacketType.PLAYER_DATA, null, usermanager);
    }

    protected void handshake(SocketConnection connection) throws IOException, GeneralSecurityException {
        AlgorithmParameters params = EncryptionDH.generateParameters(connection.readRaw());
        PublicKey serverPublicKey = EncryptionDH.getPublicKey(connection.readRaw());
        KeyPair keyPair = EncryptionDH.generate(params);
        SecretKey keyDB = EncryptionDH.combine(keyPair.getPrivate(), serverPublicKey);
        connection.sendRaw(keyPair.getPublic().getEncoded());
        connection.sendRaw(EncryptionDH.encrypt(keyDB, encryptionRSA.getUUID().toString().getBytes()));
        try {
            connection.setEncryption(new EncryptionAES(encryptionRSA.decrypt(EncryptionDH.decrypt(keyDB, connection.readRaw()))));
            connection.send("ACK".getBytes());
            if (!new String(connection.read().decrypted()).equals("ACK")) {
                // Tests that the client has the decrypted AES key
                throw new InvalidKeyException("Invalid key");
            }
        } catch (EOFException e) {
            throw new InvalidKeyException("Server ended connection during authentication");
        }
        byte[] myVersion = PROTOCOL_VERSION.getBytes();
        connection.send(myVersion);
        byte[] otherVersion = connection.read().decrypted();
        if (!Arrays.equals(otherVersion, myVersion)) {
            plugin.warning("Mismatched protocol versions, I'm on " + PROTOCOL_VERSION + ", server is on " + new String(otherVersion) + ", shutting down");
            close();
            return;
        }
        connection.setName(new String(connection.read().decrypted()));
        byte[] connectionIDBytes = connection.read().decrypted();
        idProvider = new IDProvider((short) ((connectionIDBytes[0] << 8) | (connectionIDBytes[1] & 0xFF)));

        plugin.print("Authenticated as " + connection.getName() + ", ID=" + idProvider.getConnectionID());
    }

    protected void connect(String host, int port) throws GeneralSecurityException, IOException {
        if (unableToConnectCount < 3 || plugin.debug()) {
            plugin.print("Client connecting to " + host + ":" + port + "...");
        }

        connection = new SocketConnection(plugin, new Socket(host, port));

        handshake(connection);

        handshakeComplete = true;
        unableToConnectCount = 0;

        usermanager.request();
    }

    /**
     * Initiates the client. This should only be called once, onEnable
     *
     * @param port Port of the proxy server
     * @return A CompletableException which will be done once the client successfully, or unsuccessfully, connects to the server. If successful, it will be completed with null, otherwise it will be completed with the error which prevented connection.
     */
    public CompletableException<Exception> start(String host, int port) {
        if (connection != null) throw new IllegalStateException("Client already started");
        plugin.scheduleAsync(this::keepAlive, 250, 500);

        handshakeComplete = false;

        plugin.newThread(() -> {
            while (!closed) {
                try {
                    try {
                        handshakeComplete = false;
                        connect(host, port);
                        if (!connectedCompletable.isDone()) connectedCompletable.complete(null);
                    } catch (ConnectException e) {
                        if (!connectedCompletable.isDone()) connectedCompletable.complete(e);

                        unableToConnectCount++;
                        if (!plugin.debug() && unableToConnectCount == 3) {
                            plugin.print("Server not available. Continuing to attempt silently...");
                        } else if (plugin.debug() || unableToConnectCount < 3) {
                            plugin.print("Server not available. Retrying...");
                        }
                        continue;
                    } catch (GeneralSecurityException e) {
                        if (!connectedCompletable.isDone()) connectedCompletable.complete(e);

                        if (unableToConnectCount < 3 || plugin.debug()) {
                            plugin.print("Failed to authenticate: " + e.getMessage());
                        }
                    } catch (IOException e) {
                        if (unableToConnectCount < 3 || plugin.debug()) {
                            if (!connectedCompletable.isDone()) {
                                connectedCompletable.complete(e);
                                return;
                            }
                            plugin.warning("Error during reconnection: ");
                            plugin.print(e);
                        }
                    }

                    try {
                        while (!closed) { // Listen for packets
                            Packet packet = connection.listen();
                            if (packet.getForward() != null) {
                                packet.setOrigin(packet.getForward());
                            } else {
                                packet.setOrigin("proxy");
                            }
                            if (!packet.isResponse() && packet instanceof PingPacket pingPacket) {
                                send(pingPacket.createResponse());
                            }
                            if (packet.getType() == PacketType.SERVER_LIST) {
                                servers = packet.getPayload().getJSONArray("servers").toList().stream().map(o -> (String) o).collect(Collectors.toUnmodifiableSet());
                            }
                            eventHandler.execute("proxy", packet);
                        }
                    } catch (NullPointerException | SocketException | EOFException e) {
                        plugin.print("Connection closed." + (closed ? "" : " Retrying..."));
                        if (plugin.debug() && !(e instanceof EOFException)) {
                            plugin.print(e);
                        }
                        if (closed) return;
                    } catch (Exception e) {
                        plugin.warning("Client crashed. Restarting...");
                        plugin.print(e);
                    } finally {
                        closeTemporary();
                    }
                } finally {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(unableToConnectCount > 3 ? 5000 : 1000);
                    } catch (InterruptedException e) {
                        plugin.warning("Failed to delay");
                        plugin.print(e);
                    }
                }
            }
        });
        return connectedCompletable;
    }

    /**
     * This should be called about once per second. Informs the server that it is
     * still connected. If no packet is received by the server for 10 seconds, the
     * client will be kicked.
     */
    public void keepAlive() {
        try {
            if (!isConnected() || closed || connection == null || !connection.isConnected()) {
                return;
            }
            connection.sendKeepAlive();
            if (System.currentTimeMillis() - connection.getTimeOfLastPacketReceived() > 10000) {
                closeTemporary();
                plugin.warning("timed out from proxy");
            }
        } catch (Exception e) {
            plugin.warning("Error while sending keepAlive:");
            plugin.print(e);
        }
    }

    /**
     * Permanently closes this instance of the client. Only call onDisable
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeTemporary();
    }

    /**
     * Will terminate the current connection to the server and cause the client to
     * attempt to reconnect.
     */
    public void closeTemporary() {
        if (connection == null) {
            return;
        }
        handshakeComplete = false;
        connection.close();
    }

    /**
     * Sends a packet!
     *
     * @return true if sent
     */
    public boolean send(Packet packet) throws IOException {
        return send(null, packet);
    }

    public boolean send(@Nullable String server, Packet packet) throws IOException {
        return send(server, packet, null);
    }

    @Override
    @Deprecated
    public boolean sendConsumer(@Nullable String server, Packet packet, Consumer<Packet> responseConsumer) throws IOException {
        return send(server, packet, responseConsumer);
    }

    @Override
    public boolean send(@Nullable String server, Packet packet, @Nullable Consumer<Packet> responseConsumer) throws IOException {
        return send(server, packet, responseConsumer, 0, null);
    }

    @Override
    public boolean send(@Nullable String server, Packet packet, @Nullable Consumer<Packet> responseConsumer, long timeoutMillis, @Nullable Runnable timeoutAction) throws IOException {
        checkAsync();
        if (server != null && !server.equals("all")) {
            if (servers == null || !servers.contains(server)) {
                return false;
            }
        }
        if (server != null) packet.setForward(server);
        if (idProvider == null) throw new IllegalStateException("Can not send packets before setting connection ID");
        packet.assignResponseID(idProvider);
        connection.send(packet, responseConsumer, timeoutMillis, timeoutAction);
        return true;
    }

    private void checkAsync() {
        if (plugin.isAsync()) return;
        plugin.warning("Synchronous call to sync");
        if (plugin.debug()) Thread.dumpStack();
    }

    /**
     * @return The name of this server according to the proxy
     */
    public String getName() {
        if (connection == null) {
            return null;
        }
        return connection.getName();
    }

    /**
     * @return Whether the client is actively connected
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isConnected() {
        if (connection == null) {
            return false;
        }
        return connection.isConnected() && connection.getEncryption() != null;
    }

    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    public Set<String> getServers() {
        return servers;
    }

    @Override
    public NetEventHandler getEventHandler() {
        return eventHandler;
    }

    public UserManager getUserManager() {
        return usermanager;
    }

    public long getTimeOfLastPacketReceived() {
        return connection.getTimeOfLastPacketReceived();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isClosed() {
        return closed;
    }

    public CompletableException<Exception> getConnectedCompletable() {
        return connectedCompletable;
    }

}
