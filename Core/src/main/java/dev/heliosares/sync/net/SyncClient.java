package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.heliosares.sync.utils.CompletableException;
import dev.kshl.kshlib.encryption.CodeGenerator;
import dev.kshl.kshlib.encryption.EncryptionAES;
import dev.kshl.kshlib.encryption.EncryptionRSA;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class SyncClient implements SyncNetCore {
    public static final String PROTOCOL_VERSION = SyncAPI.PROTOCOL_VERSION;
    private final SyncCore plugin;
    private final NetEventHandler eventHandler;
    private final UserManager usermanager;
    private final EncryptionRSA clientRSA;
    private final EncryptionRSA serverRSA;
    private SocketConnection connection;
    private boolean closed;
    private int unableToConnectCount = 0;
    private final Map<String, P2PServerData> servers = new HashMap<>();
    IDProvider idProvider;
    private boolean handshakeComplete;
    private final CompletableException<Exception> connectedCompletable = new CompletableException<>();
    private P2PServer p2pServer;

    @FunctionalInterface
    public interface CreatorFunction {
        SyncClient create(SyncCore plugin, EncryptionRSA clientRSA, EncryptionRSA serverRSA);
    }

    public SyncClient(SyncCore plugin, EncryptionRSA clientRSA, EncryptionRSA serverRSA) {
        this(plugin, clientRSA, serverRSA, true);
    }

    protected SyncClient(SyncCore plugin, EncryptionRSA clientRSA, EncryptionRSA serverRSA, boolean userManager) {
        this.plugin = plugin;
        this.eventHandler = new NetEventHandler(plugin);
        this.clientRSA = clientRSA;
        this.serverRSA = serverRSA;
        if (userManager) {
            this.usermanager = new UserManager(plugin, this);
            eventHandler.registerListener(PacketType.PLAYER_DATA, null, usermanager);
        } else {
            this.usermanager = null;
        }
    }

    @Nullable
    public Boolean hasWritePermission(String name) {
        var client = servers.get(name);
        if (client == null) return null;
        return client.write();
    }

    public String getRSAUserID() {
        return clientRSA.getUUID().toString();
    }

    protected void handshake(SocketConnection connection) throws IOException, GeneralSecurityException {

        byte[] myVersion = PROTOCOL_VERSION.getBytes();
        plugin.debug("Sending protocol version v" + PROTOCOL_VERSION);
        connection.sendRaw(myVersion);
        byte[] otherVersion = connection.readRaw();
        if (!Arrays.equals(otherVersion, myVersion)) {
            plugin.warning("Mismatched protocol versions, I'm on " + PROTOCOL_VERSION + ", server is on " + new String(otherVersion) + ", shutting down");
            close();
            return;
        }

        plugin.debug("Sending RSA ID");
        connection.sendRaw(serverRSA.encrypt(getRSAUserID().getBytes()));
        String clientNonce = CodeGenerator.generateSecret(32, true, true, true);
        connection.sendRaw(serverRSA.encrypt(clientNonce.getBytes()));
        try {
            connection.setEncryption(new EncryptionAES(clientRSA.decrypt(connection.readRaw())));
            plugin.debug("Received AES key");

            final String nonce = new String(serverRSA.decrypt(connection.read().decrypted()));
            if (!nonce.endsWith(" " + clientNonce) || nonce.length() != 65) {
                // Tests that the client has the decrypted AES key and confirms identity of server (encrypted with server private key)
                throw new InvalidKeyException("Invalid nonce");
            }
            plugin.debug("Received nonce: " + nonce + ", sending ack");
            connection.send(clientRSA.encrypt(("ACK " + nonce).getBytes()));

        } catch (EOFException e) {
            throw new InvalidKeyException("Server ended connection during authentication");
        }
        plugin.debug("Protocol version match");
        connection.setName(new String(connection.read().decrypted()));
        plugin.debug("Received name: " + getName());
        byte[] connectionIDBytes = connection.read().decrypted();
        idProvider = new IDProvider((short) ((connectionIDBytes[0] << 8) | (connectionIDBytes[1] & 0xFF)));
        plugin.debug("Received connection ID: " + idProvider.getConnectionID());
        plugin.debug("Starting P2P Server...");
        p2pServer = new P2PServer(plugin, this::getIDProvider);
        p2pServer.start();
        tr:
        try {
            for (int i = 0; i < 500; i++) {
                Thread.sleep(1);
                if (p2pServer.getPort() > 0) break tr;
            }
            throw new IOException("Failed to find port/start P2P server.");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        plugin.debug("P2P server running on port " + p2pServer.getPort());
        connection.send(ByteBuffer.allocate(4).putInt(p2pServer.getPort()).array());

        plugin.print("Authenticated as " + connection.getName() + ", ID=" + idProvider.getConnectionID());
    }

    protected void connect(String host, int port) throws GeneralSecurityException, IOException {
        if (unableToConnectCount < 3 || plugin.debug()) {
            plugin.print("Client connecting to " + host + ":" + port + "...");
        }

        connection = new SocketConnection(plugin, new Socket(host, port), this::getIDProvider);

        handshake(connection);

        handshakeComplete = true;
        unableToConnectCount = 0;

        if (usermanager != null) usermanager.request();
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
                            plugin.print("Failed to authenticate: " + e.getClass().getName() + " " + e.getMessage());
                        }
                        continue;
                    } catch (IOException e) {
                        if (unableToConnectCount < 3 || plugin.debug()) {
                            if (!connectedCompletable.isDone()) {
                                connectedCompletable.complete(e);
                                return;
                            }
                            plugin.print("Error during reconnection", e);
                        }
                    }

                    try {
                        while (!closed) { // Listen for packets
                            Packet packet = connection.listen();
                            if (!packet.isResponse() && packet instanceof PingPacket pingPacket) {
                                send(pingPacket.createResponse());
                            }
                            if (packet.getType() == PacketType.SERVER_LIST) {
                                JSONArray arr = packet.getPayload().getJSONArray("servers");

                                Set<String> unhandled = new HashSet<>(servers.keySet());
                                for (Object o : arr) {
                                    if (!(o instanceof JSONObject json)) continue;
                                    String name = json.getString("name");
                                    if (name.equals(getName())) continue;
                                    boolean write = json.getBoolean("write");
                                    unhandled.remove(name);
                                    String newHost = json.optString("p2p_host", null);
                                    int newPort = 0;
                                    if (newHost != null) {
                                        newPort = json.getInt("p2p_port");
                                    }
                                    P2PServerData previous = servers.get(name);
                                    if (previous != null) {
                                        if (Objects.equals(newHost, previous.host()) && previous.port() == newPort && previous.write() == write) {
                                            continue; // no change (same p2p server)
                                        }
                                    }

                                    if (previous != null && previous.client() != null && !previous.host().equals(newHost)) {
                                        // had a host, but there is now no host or there is a replacement host. Disconnect.
                                        previous.client().close();
                                        servers.put(name, null);
                                    }
                                    if (previous == null || previous.host() == null) {
                                        // Have a new host, connect
                                        P2PServerData newContainer;
                                        if (newHost == null) {
                                            newContainer = new P2PServerData(null, 0, null, write);
                                        } else {
                                            newContainer = new P2PServerData(newHost, newPort, new P2PClient(plugin, name), write);
                                            newContainer.client().start(newHost, newPort);
                                            newContainer.client().idProvider = idProvider;
                                        }
                                        servers.put(name, newContainer);
                                    }
                                }
                                for (String s : unhandled) {
                                    var prev = servers.get(s);
                                    if (prev != null && prev.client() != null) {
                                        prev.client().close();
                                    }
                                    servers.remove(s);
                                }
                            }
                            eventHandler.execute(packet.getOrigin() == null ? "proxy" : packet.getOrigin(), packet);
                        }
                    } catch (NullPointerException | SocketException | EOFException e) {
                        plugin.print("Connection closed." + (closed ? "" : " Retrying..."));
                        if (plugin.debug() && !(e instanceof EOFException)) {
                            plugin.print(null, e);
                        }
                        if (closed) return;
                    } catch (Exception e) {
                        plugin.print("Client crashed. Restarting...", e);
                    } finally {
                        closeTemporary();
                    }
                } finally {
                    try {
                        // Just a delay before attempting to reconnect - not busy waiting.
                        //noinspection BusyWait
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
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
            if (!isConnected() || closed || connection == null || !connection.isConnected() || idProvider == null) {
                return;
            }
            connection.sendKeepAlive(idProvider);
            if (System.currentTimeMillis() - connection.getTimeOfLastPacketReceived() > 10000) {
                closeTemporary();
                plugin.warning("timed out from proxy");
            }
        } catch (Exception e) {
            plugin.print("Error while sending keep-alive", e);
        }
    }

    /**
     * Permanently closes this instance of the client. Only call onDisable
     */
    public void close() {
        if (closed) return;
        closed = true;
        if (connection != null) connection.close();
    }

    /**
     * Will terminate the current connection to the server and cause the client to
     * attempt to reconnect.
     */
    public void closeTemporary() {
        if (closed || connection == null) return;
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
            if (!servers.containsKey(server)) return false;
        }
        if (idProvider == null) throw new IllegalStateException("Can not send packets before setting connection ID");
        packet.assignResponseID(idProvider);
        P2PServerData serverData = servers.get(server);
        if (serverData != null && serverData.client() != null && serverData.client().isConnected() && serverData.client().isHandshakeComplete()) {
            return serverData.client().send(null, packet, responseConsumer, timeoutMillis, timeoutAction);
        }
        if (p2pServer != null) {
            for (ServerClientHandler client : p2pServer.getClients()) {
                if (!client.isConnected()) continue;
                if (client.isClosed()) continue;
                if (!client.isHandshakeComplete()) continue;
                if (!client.getName().equals(server)) continue;

                client.send(packet, responseConsumer, timeoutMillis, timeoutAction);
                return true;
            }
        }
        if (server != null) packet.setForward(server);
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
        return servers.keySet();
    }

    public boolean hasP2PConnectionTo(String server) {
        P2PServerData serverData = servers.get(server);
        if (serverData == null) return false;
        if (serverData.client() == null) return false;
        return serverData.client().isConnected() && serverData.client().isHandshakeComplete();
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

    public CompletableException<Exception> getConnectedCompletable() {
        return connectedCompletable;
    }

    public IDProvider getIDProvider() {
        return idProvider;
    }

}
